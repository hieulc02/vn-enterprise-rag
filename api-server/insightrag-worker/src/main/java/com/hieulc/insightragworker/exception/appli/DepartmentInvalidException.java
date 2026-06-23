package com.hieulc.insightragworker.exception.appli;

import lombok.Getter;

@Getter
public class DepartmentInvalidException extends EntityNotFoundException {

    private final String fileKey;
    private final String invalidDepartment;

    public DepartmentInvalidException(String fileKey, String invalidDepartment) {
        super(String.format("Department tag '%s' on file '%s' is invalid", fileKey, invalidDepartment));
        this.fileKey = fileKey;
        this.invalidDepartment = invalidDepartment;
    }
}
