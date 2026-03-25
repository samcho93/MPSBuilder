package com.example.mpsbuilder.data.repository;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class WorkbenchLayoutRepositoryImpl_Factory implements Factory<WorkbenchLayoutRepositoryImpl> {
  private final Provider<Context> contextProvider;

  public WorkbenchLayoutRepositoryImpl_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public WorkbenchLayoutRepositoryImpl get() {
    return newInstance(contextProvider.get());
  }

  public static WorkbenchLayoutRepositoryImpl_Factory create(Provider<Context> contextProvider) {
    return new WorkbenchLayoutRepositoryImpl_Factory(contextProvider);
  }

  public static WorkbenchLayoutRepositoryImpl newInstance(Context context) {
    return new WorkbenchLayoutRepositoryImpl(context);
  }
}
