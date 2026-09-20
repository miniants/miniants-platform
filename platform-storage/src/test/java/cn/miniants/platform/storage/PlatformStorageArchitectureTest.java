package cn.miniants.platform.storage;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "cn.miniants.platform.storage", importOptions = ImportOption.DoNotIncludeTests.class)
class PlatformStorageArchitectureTest {

    @ArchTest
    static final ArchRule publicApiHidesMinio =
            noClasses()
                    .that()
                    .haveSimpleName("ObjectStorage")
                    .or()
                    .haveSimpleName("ObjectStorageWebAdapter")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("io.minio..")
                    .because("对象存储公开 API 不泄漏厂商类型");
}
