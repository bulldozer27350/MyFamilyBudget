module com.moe.myfamilybudget.domain.tax {
    requires transitive com.moe.myfamilybudget.domain.retirement;

    exports com.moe.myfamilybudget.domain.tax.calculation;
    exports com.moe.myfamilybudget.domain.tax.model;
    exports com.moe.myfamilybudget.domain.tax.port;
}
