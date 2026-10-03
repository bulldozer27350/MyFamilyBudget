module com.moe.myfamilybudget.domain.treasury {
    requires transitive com.moe.myfamilybudget.domain.budget;
    requires transitive com.moe.myfamilybudget.domain.retirement;
    requires transitive com.moe.myfamilybudget.domain.tax;
    requires transitive com.moe.myfamilybudget.domain.wealth;

    exports com.moe.myfamilybudget.domain.treasury.calculation;
    exports com.moe.myfamilybudget.domain.treasury.model;
    exports com.moe.myfamilybudget.domain.treasury.port;
}
