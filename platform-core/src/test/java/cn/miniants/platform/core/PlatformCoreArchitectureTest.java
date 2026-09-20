package cn.miniants.platform.core;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "cn.miniants.platform.core", importOptions = ImportOption.DoNotIncludeTests.class)
class PlatformCoreArchitectureTest {

    @ArchTest
    static final ArchRule noServlet =
            noClasses()
                    .that()
                    .resideInAnyPackage("cn.miniants.platform.core..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("jakarta.servlet..", "org.springframework.web..")
                    .because("platform-core 不传递 WebMVC / Servlet");

    @ArchTest
    static final ArchRule noJwyPackages =
            noClasses()
                    .that()
                    .resideInAnyPackage("cn.miniants.platform.core..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("cn.edu.ustb..")
                    .because("内核不得依赖采用方包");
}
