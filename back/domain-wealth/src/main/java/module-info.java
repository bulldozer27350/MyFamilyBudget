module com.moe.myfamilybudget.domain.wealth {
    requires transitive com.moe.myfamilybudget.domain.budget;

    exports com.moe.myfamilybudget.domain.wealth.calculation;
    exports com.moe.myfamilybudget.domain.wealth.model;
    exports com.moe.myfamilybudget.domain.wealth.port;
}
