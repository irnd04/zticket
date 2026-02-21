package kr.jemi.zticket.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

@AnalyzeClasses(packages = "kr.jemi.zticket", importOptions = ImportOption.DoNotIncludeTests.class)
class NamingConventionRuleTest {

    @ArchTest
    static final ArchRule Controller는_infrastructure_in에_위치 =
        classes()
            .that().haveSimpleNameEndingWith("Controller")
            .and().resideOutsideOfPackage("..common..")
            .should().resideInAPackage("..infrastructure.in..");

    @ArchTest
    static final ArchRule Adapter는_infrastructure_out에_위치 =
        classes()
            .that().haveSimpleNameEndingWith("Adapter")
            .should().resideInAPackage("..infrastructure.out..");

    @ArchTest
    static final ArchRule UseCase는_port_in에_위치 =
        classes()
            .that().haveSimpleNameEndingWith("UseCase")
            .should().resideInAPackage("..application.port.in..");

    @ArchTest
    static final ArchRule Port는_application_port에_위치 =
        classes()
            .that().haveSimpleNameEndingWith("Port")
            .should().resideInAPackage("..application.port..");

    @ArchTest
    static final ArchRule Facade는_api에_위치 =
        classes()
            .that().haveSimpleNameEndingWith("Facade")
            .should().resideInAPackage("..api..");

    @ArchTest
    static final ArchRule Scheduler는_infrastructure_in에_위치 =
        classes()
            .that().haveSimpleNameEndingWith("Scheduler")
            .should().resideInAPackage("..infrastructure.in..");
}
