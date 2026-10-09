package com.keepsake.memory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.keepsake.common.pagination.InvalidRequestException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class BoundingBoxTest {

    @Test
    void parsesBoxesIncludingOnesAcrossTheAntimeridian() {
        BoundingBox europe = BoundingBox.parse("-10.5, 35, 30, 60");
        assertThat(europe).isEqualTo(new BoundingBox(-10.5, 35, 30, 60));
        assertThat(europe.crossesAntimeridian()).isFalse();
        assertThat(BoundingBox.parse("170,-20,-170,0").crossesAntimeridian()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1,2,3", "1,2,3,4,5", "a,b,c,d", "-181,0,10,10", "0,-91,10,10", "0,10,10,5",
            "NaN,0,1,1", "Infinity,0,1,1"})
    void rejectsMalformedOrOutOfRangeBoxes(String value) {
        assertThatThrownBy(() -> BoundingBox.parse(value)).isInstanceOf(InvalidRequestException.class);
    }
}
