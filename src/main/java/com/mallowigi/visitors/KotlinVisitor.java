package com.mallowigi.visitors;

import com.intellij.codeInsight.daemon.impl.HighlightVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import com.mallowigi.search.ColorSearchEngine;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.psi.KtFile;

import java.awt.*;

public class KotlinVisitor extends ColorVisitor {
  @Override
  public boolean suitableForFile(@NotNull final PsiFile file) {
    return file instanceof KtFile;
  }

  @Override
  public void visit(@NotNull final PsiElement element) {
    if (!"INTEGER_CONSTANT".equals(PsiUtilCore.getElementType(element).toString())) { // NON-NLS
      return;
    }

    final String value = element.getText();
    final Color color = ColorSearchEngine.getColor(value);

    if (color != null) {
      highlight(element, color);
    }
  }

  @NotNull
  @Override
  public HighlightVisitor clone() {
    return new KotlinVisitor();
  }
}
