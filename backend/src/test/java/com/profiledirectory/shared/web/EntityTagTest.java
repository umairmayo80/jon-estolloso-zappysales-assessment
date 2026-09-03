package com.profiledirectory.shared.web;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.profiledirectory.shared.error.PreconditionFailedException;
import com.profiledirectory.shared.error.PreconditionRequiredException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EntityTagTest {
    @Test
    void requiresAnExactStrongVersionTag() {
        String tag = EntityTag.forUser(UUID.randomUUID(), 4);

        assertThatThrownBy(() -> EntityTag.requireMatch(null, tag)).isInstanceOf(PreconditionRequiredException.class);
        assertThatThrownBy(() -> EntityTag.requireMatch("W/" + tag, tag)).isInstanceOf(PreconditionFailedException.class);
        EntityTag.requireMatch(tag, tag);
    }
}
