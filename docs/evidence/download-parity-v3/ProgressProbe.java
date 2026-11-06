import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import com.yausername.youtubedl_android.StreamProcessExtractor;
public class ProgressProbe {
 public static void main(String[] args) throws Exception {
  String input = String.join("\n",
   "[youtube] Extracting URL: synthetic",
   "[download]  42.5% of 10.00MiB at 1.0MiB/s ETA 00:12",
   "[download] 100% of 10.00MiB in 00:05",
   "[download]   3.0% of 1.00MiB at 1.0MiB/s ETA 00:02",
   "[Merger] Merging formats into synthetic.mp4",
   "size= 1234kB time=00:00:01.00 bitrate=100kbits/s",
   "[ExtractAudio] Destination: synthetic.mp3") + "\n";
  var extractor = new StreamProcessExtractor(new StringBuffer(), new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
   (percent, eta, line) -> { System.out.printf(java.util.Locale.ROOT,"progress=%.1f eta=%d line=%s%n",percent,eta,line); return kotlin.Unit.INSTANCE; });
  extractor.join();
 }
}
