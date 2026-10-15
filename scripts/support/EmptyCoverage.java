import java.io.File;
import org.jacoco.core.tools.ExecFileLoader;

/** Generates empty (zero execution) test data with the same JaCoCo library as the build. */
class EmptyCoverage {
  public static void main(String[] args) throws Exception {
    new ExecFileLoader().save(new File(args[0]), false);
  }
}
