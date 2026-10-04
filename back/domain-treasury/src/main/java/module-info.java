module com.moe.myfamilybudget.domain.treasury {
    requires transitive com.moe.myfamilybudget.domain.budget;

    exports com.moe.myfamilybudget.domain.treasury.calculation;
    exports com.moe.myfamilybudget.domain.treasury.model;
    exports com.moe.myfamilybudget.domain.treasury.port;
}
