package dev.dsf.utils.validator;

import java.nio.file.Path;

public interface DsfValidator {
    ValidationOutput validate(Path path);
}
