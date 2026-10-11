package de.frauas.design.backend.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JODATIME;

/**
 * ArchUnit tests enforcing architecture constraints of the design-daas-backend.
 */
@AnalyzeClasses(packages = "de.frauas.design.backend", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {

    // -------------------------------------------------------------------------
    // Layered Architecture
    // -------------------------------------------------------------------------

    @ArchTest
    static final ArchRule layerDependencies = layeredArchitecture()
            .consideringAllDependencies()
            .layer("Config")
            .definedBy("de.frauas.design.backend.config..")
            .layer("Controllers")
            .definedBy("de.frauas.design.backend..controller..")
            .layer("Services")
            .definedBy("de.frauas.design.backend..service..")
            .layer("Repositories")
            .definedBy("de.frauas.design.backend..repository..")
            .layer("Models")
            .definedBy("de.frauas.design.backend..model..")
            .layer("DTOs")
            .definedBy("de.frauas.design.backend..dto..")
            .layer("Shared")
            .definedBy("de.frauas.design.backend.shared..")
            .layer("Bootstrap")
            .definedBy("de.frauas.design.backend.bootstrap..")
            .whereLayer("Controllers")
            .mayNotBeAccessedByAnyLayer()
            .whereLayer("Services")
            .mayOnlyBeAccessedByLayers("Controllers", "Config", "Services", "Bootstrap")
            .whereLayer("Repositories")
            .mayOnlyBeAccessedByLayers("Services", "Config", "Bootstrap")
            .whereLayer("Models")
            .mayOnlyBeAccessedByLayers("Services", "Repositories", "Config", "DTOs", "Bootstrap");

    // -------------------------------------------------------------------------
    // Naming Conventions
    // -------------------------------------------------------------------------

    @ArchTest
    static final ArchRule servicesShouldBeSuffixedService = classes()
            .that()
            .areAnnotatedWith(Service.class)
            .should()
            .haveSimpleNameEndingWith("Service")
            .orShould()
            .haveSimpleNameEndingWith("ServiceImpl")
            .because("Spring @Service classes should be suffixed with Service or ServiceImpl");

    @ArchTest
    static final ArchRule repositoriesShouldBeSuffixedRepository = classes()
            .that()
            .areAnnotatedWith(Repository.class)
            .should()
            .haveSimpleNameEndingWith("Repository")
            .because("Spring @Repository classes should be suffixed with Repository");

    @ArchTest
    static final ArchRule controllersShouldBeSuffixedController = classes()
            .that()
            .areAnnotatedWith(RestController.class)
            .should()
            .haveSimpleNameEndingWith("Controller")
            .because("Spring @RestController classes should be suffixed with Controller");

    // -------------------------------------------------------------------------
    // Package Rules
    // -------------------------------------------------------------------------

    @ArchTest
    static final ArchRule servicesShouldResideInServicePackage = classes()
            .that()
            .areAnnotatedWith(Service.class)
            .should()
            .resideInAPackage("..service..")
            .because("@Service beans must live in a service package");

    @ArchTest
    static final ArchRule repositoriesShouldResideInRepositoryPackage = classes()
            .that()
            .areAnnotatedWith(Repository.class)
            .should()
            .resideInAPackage("..repository..")
            .because("@Repository beans must live in a repository package");

    @ArchTest
    static final ArchRule controllersShouldResideInControllerPackage = classes()
            .that()
            .areAnnotatedWith(RestController.class)
            .should()
            .resideInAPackage("..controller..")
            .because("@RestController beans must live in a controller package");

    // -------------------------------------------------------------------------
    // General Coding Rules
    // -------------------------------------------------------------------------

    @ArchTest
    static final ArchRule noJavaUtilLogging =
            NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING.because("Use SLF4J (@Slf4j) for logging");

    @ArchTest
    static final ArchRule noSystemOut =
            NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS.because("Use SLF4J (@Slf4j) for output, not System.out/err");

    @ArchTest
    static final ArchRule noJodaTime =
            NO_CLASSES_SHOULD_USE_JODATIME.because("Use java.time (JSR-310) instead of Joda-Time");

    // -------------------------------------------------------------------------
    // Dependency Rules
    // -------------------------------------------------------------------------

    @ArchTest
    static final ArchRule modelsMustNotDependOnServices = noClasses()
            .that()
            .resideInAPackage("..model..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..service..")
            .because("Domain models must not depend on the service layer");

    @ArchTest
    static final ArchRule modelsMustNotDependOnControllers = noClasses()
            .that()
            .resideInAPackage("..model..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..controller..")
            .because("Domain models must not depend on controllers");

    @ArchTest
    static final ArchRule repositoriesMustNotDependOnServices = noClasses()
            .that()
            .resideInAPackage("..repository..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..service..")
            .because("Repositories must not depend on services");

    @ArchTest
    static final ArchRule dtosMustNotDependOnRepositories = noClasses()
            .that()
            .resideInAPackage("..dto..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..repository..")
            .because("DTOs must not depend on repositories");

    @ArchTest
    static final ArchRule dtosMustNotDependOnServicesOrControllers = noClasses()
            .that()
            .resideInAPackage("..dto..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..service..", "..controller..")
            .because("DTOs are plain data carriers and must not know the layers that use them");

    @ArchTest
    static final ArchRule servicesMustNotDependOnControllers = noClasses()
            .that()
            .resideInAPackage("..service..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..controller..")
            .because("Dependencies point downwards: controllers call services, never the reverse");

    @ArchTest
    static final ArchRule sharedMustNotDependOnFeatureModules = noClasses()
            .that()
            .resideInAPackage("de.frauas.design.backend.shared..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "de.frauas.design.backend.auth..",
                    "de.frauas.design.backend.user..",
                    "de.frauas.design.backend.desktop..",
                    "de.frauas.design.backend.permission..")
            .because("shared code is a leaf: feature modules depend on it, not the other way round");

    // -------------------------------------------------------------------------
    // Transactional Annotation Rules
    // -------------------------------------------------------------------------

    @ArchTest
    static final ArchRule transactionalOnlyOnServicesOrRepositories = noClasses()
            .that()
            .resideInAPackage("..controller..")
            .should()
            .beAnnotatedWith(Transactional.class)
            .because("@Transactional should be placed on service or repository layer, not controllers");

    // -------------------------------------------------------------------------
    // Field injection not allowed (prefer constructor injection)
    // -------------------------------------------------------------------------

    @ArchTest
    static final ArchRule noFieldInjectionInServices = noFields()
            .that()
            .areDeclaredInClassesThat()
            .resideInAPackage("..service..")
            .should()
            .beAnnotatedWith(org.springframework.beans.factory.annotation.Autowired.class)
            .because("Use constructor injection (e.g. @RequiredArgsConstructor) instead of @Autowired field injection"
                    + " in services");
}
