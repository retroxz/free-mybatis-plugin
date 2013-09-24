package com.seventh7.mybatis.intention;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.seventh7.mybatis.MybatisFileTemplateDescriptorFactory;
import com.seventh7.mybatis.service.EditorService;
import com.seventh7.mybatis.ui.ClickableListener;
import com.seventh7.mybatis.ui.ListSelectionListener;
import com.seventh7.mybatis.ui.UiComponentFacade;
import com.seventh7.mybatis.util.MapperUtils;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Properties;

/**
 * @author yanglin
 */
public class GenerateMapperIntention extends GenericIntention {

  @NotNull @Override
  public String getText() {
    return "[Mybatis] Generate mapper of xml";
  }

  @Override @NotNull
  public IntentionChooser getIntentionChooser() {
    return JavaFileIntentionChooser.GENERATE_MAPPER_CHOOSER;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    PsiClass clzz = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    Collection<PsiDirectory> directories = MapperUtils.findMapperDirectories(project);
    if (0 == directories.size()) {
      handleChooseNewFolder(project, editor, clzz);
    } else {
      handleMutilDirectories(project, editor, clzz, directories);
    }
  }

  private void handleMutilDirectories(Project project,
                                      final Editor editor,
                                      final PsiClass clzz,
                                      Collection<PsiDirectory> directories) {
    final Map<String, PsiDirectory> pathMap = getPathMap(directories);
    final ArrayList<String> keys = Lists.newArrayList(pathMap.keySet());
    ListSelectionListener popupListener = new ListSelectionListener() {
      @Override
      public void selected(int index) {
        processGenerate(editor, clzz, pathMap.get(keys.get(index)));
      }

      @Override
      public boolean isWriteAction() {
        return true;
      }
    };
    UiComponentFacade uiComponentFacade = UiComponentFacade.getInstance(project);
    uiComponentFacade.showListPopupWithSingleClickable("Choose folder",
                                                       popupListener,
                                                       "Choose another",
                                                       getChooseFolderListener(editor, clzz),
                                                       getPathText(project, keys));
  }

  private ClickableListener getChooseFolderListener(final Editor editor, final PsiClass clzz) {
    final Project project = clzz.getProject();
    return new ClickableListener() {
      @Override
      public void clicked() {
        handleChooseNewFolder(project, editor, clzz);
      }

      @Override
      public boolean isWriteAction() {
        return false;
      }
    };
  }

  private void handleChooseNewFolder(Project project, Editor editor, PsiClass clzz) {
    UiComponentFacade uiComponentFacade = UiComponentFacade.getInstance(project);
    VirtualFile baseDir = project.getBaseDir();
    VirtualFile vf = uiComponentFacade.showSingleFolderSelectionDialog("Select target folder", baseDir, baseDir);
    if (null != vf) {
      PsiManager psiManager = PsiManager.getInstance(project);
      processGenerate(editor, clzz, psiManager.findDirectory(vf));
    }
  }

  private String[] getPathText(Project project, Collection<String> paths) {
    final String projectBasePath = project.getBasePath();
    Collection<String> transform = Collections2.transform(paths, new Function<String, String>() {
      @Override
      public String apply(String input) {
        return FileUtil.getRelativePath(projectBasePath, input, File.separatorChar);
      }
    });
    return transform.toArray(new String[transform.size()]);
  }

  private Map<String, PsiDirectory> getPathMap(Collection<PsiDirectory> directories) {
    Map<String, PsiDirectory> result = Maps.newHashMap();
    for (PsiDirectory directory : directories) {
      result.put(directory.toString().replace("PsiDirectory:", ""), directory);
    }
    return result;
  }

  private void processGenerate(Editor editor, PsiClass clzz, PsiDirectory directory) {
    if (null == directory) {
      return;
    }
    if (!directory.isWritable()) {
      HintManager.getInstance().showErrorHint(editor, "Target directory is not writable");
      return;
    }
    try {
      Properties properties = new Properties();
      properties.setProperty("NAMESPACE", clzz.getQualifiedName());
      PsiElement psiFile = MapperUtils.createMapperFromFileTemplate(MybatisFileTemplateDescriptorFactory.MYBATIS_MAPPER_XML_TEMPLATE,
                                                                    clzz.getName(), directory, properties);
      EditorService.getInstance(clzz.getProject()).scrollTo(psiFile, 0);
    } catch (Exception e) {
      HintManager.getInstance().showErrorHint(editor, "Failed: " + e.getCause());
    }
  }

}