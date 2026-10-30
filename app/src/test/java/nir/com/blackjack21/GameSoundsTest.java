package nir.com.blackjack21;

import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Mute persistence and lifecycle silence for the placeholder sound engine. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class GameSoundsTest {

    @Test public void mutePersistsAcrossReads() {
        android.content.Context c = RuntimeEnvironment.getApplication();
        c.getSharedPreferences("game_sounds", 0).edit().clear().commit();
        assertTrue(GameSounds.isEnabled(c));
        assertFalse(GameSounds.toggle(c));
        assertFalse(GameSounds.isEnabled(c));
        assertTrue(GameSounds.toggle(c));
        assertTrue(GameSounds.isEnabled(c));
    }

    @Test public void synthBuffersAreNonSilent() {
        int[] all = {GameSounds.CLICK, GameSounds.CHIP, GameSounds.CARD, GameSounds.SPIN,
                GameSounds.WIN, GameSounds.BIG_WIN, GameSounds.LOSE, GameSounds.LEVEL_UP};
        for (int snd : all) {
            short[] pcm = GameSounds.synth(snd);
            assertNotNull("synth " + snd, pcm);
            assertTrue("synth " + snd + " too short", pcm.length > 500);
            int peak = 0;
            for (short v : pcm) peak = Math.max(peak, Math.abs((int) v));
            assertTrue("synth " + snd + " silent", peak > 2000);
        }
    }

    /** Writes the exact generated PCM as WAVs to /tmp/sfx for offline audition. */
    @Test public void dumpSfxWavs() throws Exception {
        String[] names = {"click", "chip", "card", "win", "bigwin", "lose", "levelup", "spin"};
        int[] ids = {GameSounds.CLICK, GameSounds.CHIP, GameSounds.CARD, GameSounds.WIN,
                GameSounds.BIG_WIN, GameSounds.LOSE, GameSounds.LEVEL_UP, GameSounds.SPIN};
        new java.io.File("/tmp/sfx").mkdirs();
        for (int k = 0; k < ids.length; k++) {
            short[] pcm = GameSounds.synth(ids[k]);
            java.io.ByteArrayOutputStream raw = new java.io.ByteArrayOutputStream();
            for (short v : pcm) { raw.write(v & 0xFF); raw.write((v >> 8) & 0xFF); }
            byte[] data = raw.toByteArray();
            java.io.FileOutputStream f = new java.io.FileOutputStream("/tmp/sfx/" + names[k] + ".wav");
            int sr = 22050;
            f.write("RIFF".getBytes());
            writeInt(f, 36 + data.length); f.write("WAVEfmt ".getBytes());
            writeInt(f, 16); writeShort(f, 1); writeShort(f, 1);
            writeInt(f, sr); writeInt(f, sr * 2); writeShort(f, 2); writeShort(f, 16);
            f.write("data".getBytes()); writeInt(f, data.length);
            f.write(data); f.close();
        }
        // background music loop
        short[] pcm = GameSounds.synthMusic();
        java.io.ByteArrayOutputStream raw = new java.io.ByteArrayOutputStream();
        for (short v : pcm) { raw.write(v & 0xFF); raw.write((v >> 8) & 0xFF); }
        byte[] data = raw.toByteArray();
        java.io.FileOutputStream f = new java.io.FileOutputStream("/tmp/sfx/music.wav");
        f.write("RIFF".getBytes());
        writeInt(f, 36 + data.length); f.write("WAVEfmt ".getBytes());
        writeInt(f, 16); writeShort(f, 1); writeShort(f, 1);
        writeInt(f, 22050); writeInt(f, 22050 * 2); writeShort(f, 2); writeShort(f, 16);
        f.write("data".getBytes()); writeInt(f, data.length);
        f.write(data); f.close();
    }

    private static void writeInt(java.io.OutputStream f, int v) throws Exception {
        f.write(v & 0xFF); f.write((v >> 8) & 0xFF); f.write((v >> 16) & 0xFF); f.write((v >> 24) & 0xFF);
    }

    private static void writeShort(java.io.OutputStream f, int v) throws Exception {
        f.write(v & 0xFF); f.write((v >> 8) & 0xFF);
    }

    @Test public void musicLoopIsNonSilent() {
        short[] pcm = GameSounds.synthMusic();
        assertNotNull(pcm);
        assertTrue(pcm.length > 100000);
        int peak = 0;
        for (short v : pcm) peak = Math.max(peak, Math.abs((int) v));
        assertTrue("music silent", peak > 1000);
        assertTrue("music may clip", peak < 25000);
    }

    @Test public void backgroundedAppIsSilent() {
        android.content.Context c = RuntimeEnvironment.getApplication();
        GameSounds.onAppResume();
        assertTrue(GameSounds.isForeground());
        GameSounds.onAppPause();
        assertFalse(GameSounds.isForeground());
        // play must no-op without throwing while backgrounded or muted
        GameSounds.play(c, GameSounds.WIN);
        GameSounds.toggle(c); // mute
        GameSounds.onAppResume();
        GameSounds.play(c, GameSounds.WIN);
        GameSounds.toggle(c); // unmute
        GameSounds.play(c, GameSounds.WIN);
        GameSounds.release();
    }
}
