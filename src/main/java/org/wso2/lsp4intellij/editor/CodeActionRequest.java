package org.wso2.lsp4intellij.editor;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.util.TextRange;

public record CodeActionRequest(IntentionAction action, TextRange range) {
}
