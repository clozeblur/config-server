package com.clozeblur.server.git.exception;

/**
 * Created by clozeblur
 * on 2017/6/16
 */
public class GitParseException extends RuntimeException {

    private static final long serialVersionUID = 5339450897157708483L;

    public GitParseException() {
        super();
    }

    public GitParseException(String message) {
        super(message);
    }

    public GitParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
