package kr.jemi.zticket.architecture;

import kr.jemi.zticket.ZticketApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModularityTest {

    @Test
    void 모듈_구조_검증() {
        ApplicationModules modules = ApplicationModules.of(ZticketApplication.class);
        modules.verify();
    }

    @Test
    void 모듈_문서_생성() {
        ApplicationModules modules = ApplicationModules.of(ZticketApplication.class);
        new Documenter(modules)
            .writeModulesAsPlantUml()
            .writeIndividualModulesAsPlantUml()
            .writeDocumentation();
    }
}
