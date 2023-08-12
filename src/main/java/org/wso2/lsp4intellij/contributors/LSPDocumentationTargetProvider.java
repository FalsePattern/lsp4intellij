package org.wso2.lsp4intellij.contributors;

import com.intellij.model.Pointer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.platform.backend.documentation.DocumentationResult;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.documentation.DocumentationTargetProvider;
import com.intellij.platform.backend.presentation.TargetPresentation;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiFileRange;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.jsonrpc.JsonRpcException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.wso2.lsp4intellij.editor.EditorEventManagerBase;
import org.wso2.lsp4intellij.requests.HoverHandler;
import org.wso2.lsp4intellij.requests.Timeout;
import org.wso2.lsp4intellij.requests.Timeouts;
import org.wso2.lsp4intellij.utils.ApplicationUtils;
import org.wso2.lsp4intellij.utils.DocumentUtils;
import org.wso2.lsp4intellij.utils.FileUtils;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class LSPDocumentationTargetProvider implements DocumentationTargetProvider {
    @Override
    public @NotNull List<? extends @NotNull DocumentationTarget> documentationTargets(@NotNull PsiFile file, int offset) {
        return Collections.singletonList(new LSPDocumentationTarget(file, offset));
    }

    public static class LSPDocumentationTarget implements DocumentationTarget {
        private final Pointer<LSPDocumentationTarget> pointer;
        private final PsiFile file;
        private final int offset;
        public LSPDocumentationTarget(PsiFile file, int offset) {
            this.file = file;
            this.offset = offset;

            var range = TextRange.from(offset, 0);
            SmartPsiFileRange base = SmartPointerManager.getInstance(file.getProject()).createSmartPsiFileRangePointer(file, range);
            pointer = new FileRangePointer(base);
        }

        protected Logger LOG = Logger.getInstance(LSPDocumentationTargetProvider.class);

        @Nullable
        @Override
        public DocumentationResult computeDocumentation() {
            var editor = FileUtils.editorFromPsiFile(file);
            if (editor == null) {
                return null;
            }
            var manager = EditorEventManagerBase.forEditor(editor);
            if (manager == null) {
                return null;
            }
            var wrapper = manager.wrapper;
            if (wrapper == null) {
                return null;
            }
            var caretPos = editor.offsetToLogicalPosition(offset);
            var serverPos = ApplicationUtils.computableReadAction(() -> DocumentUtils.logicalToLSPPos(caretPos, editor));
            return DocumentationResult.asyncDocumentation(() -> {
                var identifier = manager.getIdentifier();
                var request = wrapper.getRequestManager().hover(new HoverParams(identifier, serverPos));
                if (request == null) {
                    return null;
                }
                try {
                    var hover = request.get(Timeout.getTimeout(Timeouts.HOVER), TimeUnit.MILLISECONDS);
                    wrapper.notifySuccess(Timeouts.HOVER);
                    if (hover == null) {
                        LOG.debug(String.format("Hover is null for file %s and pos (%d;%d)", identifier.getUri(),
                                                serverPos.getLine(), serverPos.getCharacter()));
                        return null;
                    }

                    String string = HoverHandler.getHoverString(hover);
                    if (StringUtils.isEmpty(string)) {
                        LOG.warn(String.format("Hover string returned is empty for file %s and pos (%d;%d)",
                                               identifier.getUri(), serverPos.getLine(), serverPos.getCharacter()));
                        return null;
                    }
                    return DocumentationResult.documentation(string);
                } catch (TimeoutException e) {
                    LOG.warn(e);
                    wrapper.notifyFailure(Timeouts.HOVER);
                } catch (InterruptedException | JsonRpcException | ExecutionException e) {
                    LOG.warn(e);
                    wrapper.crashed(e);
                }
                return null;
            });
        }

        @NotNull
        @Override
        public TargetPresentation computePresentation() {
            return TargetPresentation.builder("Doc from language server").presentation();
        }

        @NotNull
        @Override
        public Pointer<? extends DocumentationTarget> createPointer() {
            return pointer;
        }

        private static class FileRangePointer implements Pointer<LSPDocumentationTarget> {
            private final SmartPsiFileRange base;
            public FileRangePointer(SmartPsiFileRange base) {
                this.base = base;
            }
            @Override
            public @Nullable LSPDocumentationTarget dereference() {
                if (base.getElement() == null) {
                    return null;
                }
                if (base.getRange() == null) {
                    return null;
                }
                return new LSPDocumentationTarget(base.getElement(), TextRange.create(base.getRange()).getStartOffset());
            }
        }
    }
}
