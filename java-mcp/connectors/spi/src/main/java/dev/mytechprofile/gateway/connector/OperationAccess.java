package dev.mytechprofile.gateway.connector;

/**
 * Side-effect classification supplied by a connector during operation discovery.
 *
 * <p>The gateway compares this value with the catalog tool mode so a mutating
 * downstream operation cannot accidentally be published as an unapproved read.
 */
public enum OperationAccess {
    /** Operation is expected to be side-effect free. */
    READ,
    /** Operation may mutate downstream state. */
    WRITE,
    /** Connector cannot determine the operation's side-effect behavior. */
    UNKNOWN
}
