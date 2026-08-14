package com.mailkube.model;

/**
 * A file attached to an email.
 *
 * @param filename the name of the attached file
 * @param content the raw file content; the SDK base64-encodes it for the wire
 * @param contentType the MIME type, inferred from the filename when null
 */
public record Attachment(String filename, byte[] content, String contentType) {

    /**
     * An attachment whose MIME type the server should infer.
     *
     * @param filename the name of the attached file
     * @param content the raw file content
     * @return the attachment
     */
    public static Attachment of(String filename, byte[] content) {
        return new Attachment(filename, content, null);
    }
}
