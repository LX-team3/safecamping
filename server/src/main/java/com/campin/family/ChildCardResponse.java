package com.campin.family;

import java.time.LocalDate;
import java.time.OffsetDateTime;

// 아이 카드 하나. 비워 둔 칸은 null
public record ChildCardResponse(Long id, String name, LocalDate birthDate, String bloodType,
                                String allergy, String disease, String medicine, String memo,
                                String updatedByName, OffsetDateTime updatedAt) {

    public static ChildCardResponse from(ChildCard c) {
        return new ChildCardResponse(c.getId(), c.getName(), c.getBirthDate(), c.getBloodType(),
                c.getAllergy(), c.getDisease(), c.getMedicine(), c.getMemo(),
                c.getUpdatedByName(), c.getUpdatedAt());
    }
}
