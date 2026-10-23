package com.khankiddo.learning.ai.conversation.model;

import com.khankiddo.learning.model.enums.ExpressionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrammarErrorDto {

    private String pointId;
    private ExpressionStatus status;
    private String point;
}
