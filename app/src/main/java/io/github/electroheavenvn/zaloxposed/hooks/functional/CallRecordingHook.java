package io.github.electroheavenvn.zaloxposed.hooks.functional;

import android.content.Context;

import java.io.File;
import java.io.RandomAccessFile;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.electroheavenvn.zaloxposed.hooks.BaseHook;
import io.github.electroheavenvn.zaloxposed.utilities.CallRecordingStore;
import io.github.electroheavenvn.zaloxposed.utilities.Config;
import io.github.electroheavenvn.zaloxposed.utilities.HookUtils;
import io.github.electroheavenvn.zaloxposed.utilities.Logger;

/**
 * One-to-one ZRTC call recorder.
 *
 * <p>Zalo's native peer already exposes a WAV recorder through
 * {@code PeerJNI.zrtc_peer_start_record_audio(long, boolean, String)}. The hook
 * starts it when the audio stream is registered and stops it on the peer
 * termination methods. Finished files stay in Zalo's app-specific data
 * directory ({@code ZaloXposed/CallRecordings}), so no storage permission is
 * needed; the settings menu can list, play, delete and export them.</p>
 */
public class CallRecordingHook extends BaseHook
{
    private static final String PEER_JNI = "com.vng.zing.vn.zrtc.PeerJNI";
    private static final String RECORD_METHOD = "zrtc_peer_start_record_audio";
    private static final long WAV_READY_TIMEOUT_MS = 5000L;
    private static final long WAV_READY_POLL_MS = 120L;

    private static final Map<Long, Session> SESSIONS = new ConcurrentHashMap<>();
    private static final ExecutorService FINALIZER = Executors.newSingleThreadExecutor();
    private static volatile Method recordAudio;

    @Override
    public void hook() throws Throwable
    {
        if (!Config.getEnableCallRecording())
        {
            Logger.i("Call recording disabled");
            return;
        }

        Class<?> peerClass = Class.forName(PEER_JNI, false, classLoader);
        recordAudio = peerClass.getDeclaredMethod(RECORD_METHOD,
            long.class, boolean.class, String.class);
        recordAudio.setAccessible(true);

        hookDirection(peerClass, "zrtc_peer_make_call", "outgoing");
        hookDirection(peerClass, "zrtc_peer_incoming_call", "incoming");
        hookAudioStream(peerClass, "zrtc_peer_register_in_audio_stream");
        hookAudioStream(peerClass, "zrtc_peer_register_out_audio_stream");
        hookTermination(peerClass, "zrtc_peer_end_call");
        hookTermination(peerClass, "zrtc_peer_force_stop");
        hookTermination(peerClass, "zrtc_peer_delete");

        Logger.i("Hooking: CallRecordingHook enabled");
    }

    private void hookDirection(Class<?> peerClass, String name, String direction)
    {
        for (Method method : peerClass.getDeclaredMethods())
        {
            if (!name.equals(method.getName()) || method.getParameterTypes().length == 0
                || method.getParameterTypes()[0] != long.class)
                continue;
            try
            {
                method.setAccessible(true);
                module.hook(method).intercept(chain ->
                {
                    Object handle = chain.getArg(0);
                    if (Config.getEnableCallRecording() && handle instanceof Long)
                    {
                        Session session = SESSIONS.computeIfAbsent((Long)handle,
                            ignored -> new Session((Long)handle));
                        session.direction = direction;
                    }
                    return chain.proceed();
                });
                Logger.i("Hooking call direction: " + method);
            }
            catch (Throwable t)
            {
                Logger.e(t);
            }
        }
    }

    private void hookAudioStream(Class<?> peerClass, String name)
    {
        for (Method method : peerClass.getDeclaredMethods())
        {
            if (!name.equals(method.getName()) || method.getParameterTypes().length == 0
                || method.getParameterTypes()[0] != long.class)
                continue;
            try
            {
                method.setAccessible(true);
                module.hook(method).intercept(chain ->
                {
                    Object result = chain.proceed();
                    Object handle = chain.getArg(0);
                    if (Config.getEnableCallRecording() && handle instanceof Long)
                        start((Long)handle);
                    return result;
                });
                Logger.i("Hooking call audio stream: " + method);
            }
            catch (Throwable t)
            {
                Logger.e(t);
            }
        }
    }

    private void hookTermination(Class<?> peerClass, String name)
    {
        for (Method method : peerClass.getDeclaredMethods())
        {
            if (!name.equals(method.getName()) || method.getParameterTypes().length == 0
                || method.getParameterTypes()[0] != long.class)
                continue;
            try
            {
                method.setAccessible(true);
                module.hook(method).intercept(chain ->
                {
                    Object handle = chain.getArg(0);
                    if (Config.getEnableCallRecording() && handle instanceof Long)
                        stop((Long)handle);
                    return chain.proceed();
                });
                Logger.i("Hooking call termination: " + method);
            }
            catch (Throwable t)
            {
                Logger.e(t);
            }
        }
    }

    private static void start(long peerHandle)
    {
        if (!Config.getEnableCallRecording() || recordAudio == null)
            return;
        Session session = SESSIONS.computeIfAbsent(peerHandle, Session::new);
        synchronized (session)
        {
            if (session.started)
                return;
            Context context = HookUtils.currentApplication();
            if (context == null)
                return;
            File directory = CallRecordingStore.directory(context);
            if (!directory.exists() && !directory.mkdirs())
            {
                Logger.e("Cannot create call recording directory");
                return;
            }
            session.file = CallRecordingStore.newRecordingFile(
                context, session.direction, peerHandle);
            try
            {
                recordAudio.invoke(null, peerHandle, true, session.file.getAbsolutePath());
                session.started = true;
                Logger.i("Call recording started: " + session.file.getName());
            }
            catch (Throwable t)
            {
                if (session.file != null)
                    session.file.delete();
                session.file = null;
                Logger.e(t);
            }
        }
    }

    private static void stop(long peerHandle)
    {
        Session session = SESSIONS.remove(peerHandle);
        if (session == null)
            return;
        File file;
        synchronized (session)
        {
            if (!session.started)
                return;
            session.started = false;
            file = session.file;
            session.file = null;
            try
            {
                if (recordAudio != null)
                    recordAudio.invoke(null, peerHandle, false,
                        file == null ? "" : file.getAbsolutePath());
            }
            catch (Throwable t)
            {
                Logger.e(t);
            }
        }
        if (file != null)
        {
            File recorded = file;
            FINALIZER.execute(() -> finalizeRecording(recorded));
        }
    }

    private static void finalizeRecording(File source)
    {
        if (!awaitNativeWav(source))
        {
            Logger.e("Call recording did not finalize: " + source.getName());
            source.delete();
            return;
        }
        Logger.i("Call recording saved: " + source.getAbsolutePath());
    }

    private static boolean awaitNativeWav(File file)
    {
        long deadline = System.currentTimeMillis() + WAV_READY_TIMEOUT_MS;
        long previous = -1L;
        int stable = 0;
        while (System.currentTimeMillis() <= deadline)
        {
            long length = file.isFile() ? file.length() : -1L;
            if (length > 44L)
            {
                if (length == previous)
                {
                    stable++;
                    if (stable >= 2)
                        break;
                }
                else
                {
                    stable = 0;
                }
            }
            previous = length;
            try
            {
                Thread.sleep(WAV_READY_POLL_MS);
            }
            catch (InterruptedException interrupted)
            {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        if (!file.isFile() || file.length() <= 44L)
            return false;
        repairWavHeader(file);
        return true;
    }

    private static void repairWavHeader(File file)
    {
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw"))
        {
            if (raf.length() < 44L)
                return;
            byte[] header = new byte[44];
            raf.readFully(header);
            if (header[0] != 'R' || header[1] != 'I' || header[2] != 'F' || header[3] != 'F'
                || header[8] != 'W' || header[9] != 'A' || header[10] != 'V' || header[11] != 'E')
                return;
            int riffSize = (int)(raf.length() - 8L);
            int dataSize = (int)(raf.length() - 44L);
            writeLittleEndian(header, 4, riffSize);
            writeLittleEndian(header, 40, dataSize);
            raf.seek(0);
            raf.write(header);
        }
        catch (Throwable t)
        {
            Logger.e(t);
        }
    }

    private static void writeLittleEndian(byte[] target, int offset, int value)
    {
        target[offset] = (byte)(value & 0xff);
        target[offset + 1] = (byte)((value >> 8) & 0xff);
        target[offset + 2] = (byte)((value >> 16) & 0xff);
        target[offset + 3] = (byte)((value >> 24) & 0xff);
    }

    private static final class Session
    {
        final long peerHandle;
        volatile String direction = "unknown";
        volatile File file;
        volatile boolean started;

        Session(long peerHandle)
        {
            this.peerHandle = peerHandle;
        }
    }
}
