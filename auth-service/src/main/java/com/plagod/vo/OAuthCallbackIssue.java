package com.plagod.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OAuthCallbackIssue {

    private final OAuthCallbackResultVO result;
    private final AuthSessionIssue sessionIssue;
}
