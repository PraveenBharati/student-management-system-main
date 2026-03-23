package com.devops.studentmanagement.exception;

public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException(String email) {
        super("Student already exists with email: " + email);
    }
}
