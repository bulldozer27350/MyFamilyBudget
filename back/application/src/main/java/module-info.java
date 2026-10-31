module com.moe.myfamilybudget.application {
    requires transitive com.moe.myfamilybudget.application.api;

    requires com.moe.myfamilybudget.domain.analysis;
    requires com.moe.myfamilybudget.domain.bankpointage;
    requires com.moe.myfamilybudget.domain.credit;
    requires com.moe.myfamilybudget.domain.goals;
    requires com.moe.myfamilybudget.domain.market;
    requires com.moe.myfamilybudget.domain.notifications;
    requires com.moe.myfamilybudget.domain.retirement;
    requires com.moe.myfamilybudget.domain.settings;
    requires com.moe.myfamilybudget.domain.tax;
    requires com.moe.myfamilybudget.domain.treasury;
    requires com.moe.myfamilybudget.domain.wealth;

    requires api;

    requires spring.beans;
    requires spring.context;
    requires org.slf4j;
    requires com.fasterxml.jackson.databind;

    exports com.moe.myfamilybudget.application.command;
    exports com.moe.myfamilybudget.application.error;
    exports com.moe.myfamilybudget.application.factory;
    exports com.moe.myfamilybudget.application.mapper;
    exports com.moe.myfamilybudget.application.overview;
    exports com.moe.myfamilybudget.application.settings;
    exports com.moe.myfamilybudget.application.snapshot;
    exports com.moe.myfamilybudget.application.usecase;
}
