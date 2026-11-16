# BJ capture transport regression

Host-only check of the exact plain-argument builder and concurrent stdout/stderr collector used by StoreCaptureScriptTest. Requires a JDK and a Unix host with mkdir, stat, cat, rm and sh. Does not prove Android shell permissions, screencap behavior, device PNGs, or Firebase collection.

From the repository root:

```sh
mkdir -p /tmp/bj-capture-transport
javac -d /tmp/bj-capture-transport app/src/androidTest/java/nir/com/blackjack21/CaptureShellTransport.java tools/CaptureShellTransportRegression.java
java -cp /tmp/bj-capture-transport nir.com.blackjack21.CaptureShellTransportRegression
```

Expected: 13 checks pass, including exact binary readback, separate stderr capture, stale-file deletion, rejection of quotes/whitespace/metacharacters, and reproduction of the former quoted-command failure.

Device driver stages: plain mkdir, rm stale pose, screencap, stat byte count, cat readback. Each uses executeShellCommandRwe with closed stdin, concurrent stdout/stderr draining, 30-second output deadline, 24 MiB stdout and 64 KiB stderr limits. Nonempty stderr fails. Stat and readback must agree; PNG signature, MIME and full-display dimensions must match. The API does not expose child exit status, so empty stderr alone is not treated as proof of capture: stat/readback checks establish the saved result. Every stage logs start, output byte count and stderr. No shell quoting, compound command, extra service, or permission changes.

API36 storage and Firebase collector remain unverified until a separately authorized device pilot returns native PNGs.
