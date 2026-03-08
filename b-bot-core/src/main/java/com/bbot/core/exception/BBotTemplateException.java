package com.bbot.core.exception;

/**
 * Thrown when a JSON template cannot be loaded from the classpath or contains
 * unresolvable tokens that prevent valid JSON generation.
 *
 * <p>Carries the {@link #templateName()} for diagnostic reporting.
 */
public class BBotTemplateException extends BBotException {

    private final String templateName;

    public BBotTemplateException(String message, String templateName) {
        super(message);
        this.templateName = templateName;
    }

    public BBotTemplateException(String message, String templateName, Throwable cause) {
        super(message, cause);
        this.templateName = templateName;
    }

    /** The template name or classpath path that triggered the error. */
    public String templateName() { return templateName; }
}

