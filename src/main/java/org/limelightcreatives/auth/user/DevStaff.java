package org.limelightcreatives.auth.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class DevStaff implements ApplicationRunner {

    private final Users users;
    private final String email;

    public DevStaff(Users users, @Value("${auth.dev-staff-email:}") String email) {
        this.users = users;
        this.email = email;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (this.email.isBlank()) return;
        this.users.promoteParticipant(this.users.findOrCreate(this.email).id(), Role.STAFF);
    }
}
