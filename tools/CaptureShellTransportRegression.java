package nir.com.blackjack21;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
public class CaptureShellTransportRegression {
    private static int checks;
    private static void check(boolean yes, String label) {
        if (!yes) throw new AssertionError(label);
        checks++; System.out.println("PASS " + label);
    }
    private static CaptureShellTransport.Result run(String... args) throws Exception {
        Process p = Runtime.getRuntime().exec(CaptureShellTransport.command(args));
        p.getOutputStream().close();
        return CaptureShellTransport.collect(p.getInputStream(), p.getErrorStream());
    }
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("capture-transport-");
        try {
            String path = dir.resolve("pose.png").toString();
            CaptureShellTransport.Result r = run("/bin/mkdir", "-p", dir.toString());
            check(r.stdout.length == 0 && r.stderr.length == 0, "plain mkdir");
            Files.write(Path.of(path), new byte[]{(byte)137,80,78,71,13,10,26,10,1});
            r = run("/usr/bin/stat", "-c", "%s", path);
            check(new String(r.stdout, StandardCharsets.UTF_8).trim().equals("9") && r.stderr.length == 0, "plain stat exact bytes");
            r = run("/bin/cat", path);
            check(java.util.Arrays.equals(r.stdout, Files.readAllBytes(Path.of(path))), "binary readback unchanged");
            r = run("/bin/cat", dir.resolve("missing.png").toString());
            check(r.stdout.length == 0 && r.stderr.length > 0, "stderr independently collected");
            run("/bin/rm", "-f", path);
            check(!Files.exists(Path.of(path)), "stale pose removed");
            for (String bad : new String[]{"a b", "'mkdir", "&&", "x;y", "$(id)", "", "a\\nb"}) {
                boolean rejected = false;
                try { CaptureShellTransport.command("echo", bad); } catch (IllegalArgumentException e) { rejected = true; }
                check(rejected, "unsafe argument rejected: " + bad);
            }
            // Reproduce former transport defect without running any Android command.
            Process broken = Runtime.getRuntime().exec("sh -c 'echo CAPTURE_OK'");
            broken.getOutputStream().close();
            CaptureShellTransport.Result old = CaptureShellTransport.collect(broken.getInputStream(), broken.getErrorStream());
            check(old.stdout.length == 0 && old.stderr.length > 0 && broken.waitFor() != 0,
                    "old quoted compound transport fails as diagnosed");
            System.out.println("HOST TRANSPORT ONLY: " + checks + " checks passed; no Android/storage/collector proof.");
        } finally {
            try (java.util.stream.Stream<Path> files = Files.walk(dir)) {
                files.sorted(java.util.Comparator.reverseOrder()).forEach(p -> { try { Files.delete(p); } catch (Exception e) { throw new RuntimeException(e); } });
            }
        }
    }
}
