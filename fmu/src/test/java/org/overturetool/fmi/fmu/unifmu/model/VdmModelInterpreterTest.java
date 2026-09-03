package org.overturetool.fmi.fmu.unifmu.model;

import com.fujitsu.vdmj.lex.Dialect;
import org.junit.jupiter.api.Test;

import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class VdmModelInterpreterTest {
    public static List<Path> findFiles(Path root, FilenameFilter filter) throws IOException {
        return Files.walk(root)
                .filter(Files::isRegularFile)
                .filter(p -> filter.accept(p.getParent().toFile(), p.getFileName().toString()))
                .collect(Collectors.toList());
    }

    @Test
    public void testInit() throws Exception {
        var interpreter = new VdmModelInterpreter(findFiles(Paths.get("src", "test", "resources", "model"), Dialect.VDM_RT.getFilter()));

        interpreter.init();
        interpreter.setValueParameter(new FmiStateModel.ModelVariable("HardwareInterface.maxlevel", FmiStateModel.ModelInterface.Value),8.9);

        interpreter.fmi2ExitInitializationMode();

    }
}
