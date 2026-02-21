package kr.jemi.zticket.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "kr.jemi.zticket", importOptions = ImportOption.DoNotIncludeTests.class)
class AnnotationRuleTest {

    @ArchTest
    static final ArchRule domain에_Spring_어노테이션_금지 =
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().beAnnotatedWith("org.springframework.stereotype.Service")
            .orShould().beAnnotatedWith("org.springframework.stereotype.Component")
            .orShould().beAnnotatedWith("org.springframework.stereotype.Repository")
            .orShould().beAnnotatedWith("org.springframework.transaction.annotation.Transactional");

    @ArchTest
    static final ArchRule JPA_Entity는_infrastructure_out에만 =
        classes()
            .that().areAnnotatedWith("jakarta.persistence.Entity")
            .should().resideInAPackage("..infrastructure.out..");

    @ArchTest
    static final ArchRule RestController는_infrastructure_in에만 =
        classes()
            .that().areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
            .should().resideInAPackage("..infrastructure.in..");
}
