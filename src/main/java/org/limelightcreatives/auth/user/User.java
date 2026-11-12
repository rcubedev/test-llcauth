package org.limelightcreatives.auth.user;

public record User(String id, String email, String firstName, String lastName, Role role) {

    public String name() {
        return needsProfile() ? null : this.firstName + " " + this.lastName;
    }

    public boolean needsProfile() {
        return this.firstName == null || this.firstName.isBlank() || this.lastName == null || this.lastName.isBlank();
    }
}
