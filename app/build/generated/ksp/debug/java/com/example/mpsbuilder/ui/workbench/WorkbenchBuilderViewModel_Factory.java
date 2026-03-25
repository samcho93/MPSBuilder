package com.example.mpsbuilder.ui.workbench;

import com.example.mpsbuilder.data.repository.WorkbenchLayoutRepository;
import com.example.mpsbuilder.ui.workbench.workpiece.WorkpieceSupplier;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation",
    "nullness:initialization.field.uninitialized"
})
public final class WorkbenchBuilderViewModel_Factory implements Factory<WorkbenchBuilderViewModel> {
  private final Provider<WorkpieceSupplier> workpieceSupplierProvider;

  private final Provider<WorkbenchLayoutRepository> layoutRepositoryProvider;

  public WorkbenchBuilderViewModel_Factory(Provider<WorkpieceSupplier> workpieceSupplierProvider,
      Provider<WorkbenchLayoutRepository> layoutRepositoryProvider) {
    this.workpieceSupplierProvider = workpieceSupplierProvider;
    this.layoutRepositoryProvider = layoutRepositoryProvider;
  }

  @Override
  public WorkbenchBuilderViewModel get() {
    return newInstance(workpieceSupplierProvider.get(), layoutRepositoryProvider.get());
  }

  public static WorkbenchBuilderViewModel_Factory create(
      Provider<WorkpieceSupplier> workpieceSupplierProvider,
      Provider<WorkbenchLayoutRepository> layoutRepositoryProvider) {
    return new WorkbenchBuilderViewModel_Factory(workpieceSupplierProvider, layoutRepositoryProvider);
  }

  public static WorkbenchBuilderViewModel newInstance(WorkpieceSupplier workpieceSupplier,
      WorkbenchLayoutRepository layoutRepository) {
    return new WorkbenchBuilderViewModel(workpieceSupplier, layoutRepository);
  }
}
