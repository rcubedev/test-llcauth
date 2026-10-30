package org.limelightcreatives.auth.user;

/** A row of "users". First and last name stay empty until the person finishes signing up. */
public record User(String id, String email, String firstName, String lastName, Role role) {

    /** "First Last", or null until both are known (what the dashboard expects). */
    public String name() {
        return needsProfile() ? null : firstName + " " + lastName;
    }

    public boolean needsProfile() {
        return firstName == null || firstName.isBlank() || lastName == null || lastName.isBlank();
    }
}
