package dev.demo.users;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class UserControllerTest {

    @Test
    void createThenGet_whenNameIsPresent_returnsTheUser() {
        UserController controller = new UserController();
        UserResponse created = controller.create(new CreateUserRequest("Ada", "ada@example.com"));
        assertEquals("Ada", created.name());
        assertEquals("Ada", controller.get(created.id()).name());
    }
}
