package com.plagod;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.generator.AutoGenerator;
import com.baomidou.mybatisplus.generator.config.DataSourceConfig;
import com.baomidou.mybatisplus.generator.config.GlobalConfig;
import com.baomidou.mybatisplus.generator.config.PackageConfig;
import com.baomidou.mybatisplus.generator.config.StrategyConfig;
import com.baomidou.mybatisplus.generator.config.rules.NamingStrategy;

public class Main {
    public static void main(String[] args) {
        AutoGenerator mpg = new AutoGenerator();

        DataSourceConfig dsc = new DataSourceConfig();
        dsc.setDbType(DbType.MYSQL);
        dsc.setDriverName("com.mysql.cj.jdbc.Driver");
        dsc.setUsername("root");
        dsc.setPassword("20040903");
        dsc.setUrl("jdbc:mysql://localhost:3306/wifi?useUnicode=true&characterEncoding=utf8");
        mpg.setDataSource(dsc);

        GlobalConfig gc = new GlobalConfig();
        gc.setOpen(false);
        gc.setOutputDir(System.getProperty("user.dir") + "/wifi-common/src/main/java");
        gc.setEntityName("%s");
        mpg.setGlobalConfig(gc);


        PackageConfig pc = new PackageConfig();
        pc.setParent("com.plagod");
        pc.setEntity("entity");
        mpg.setPackageInfo(pc);

        StrategyConfig strategy = new StrategyConfig();
        strategy.setEntityLombokModel(true);
        strategy.setInclude("user");
        strategy.setNaming(NamingStrategy.underline_to_camel);
        strategy.setColumnNaming(NamingStrategy.underline_to_camel);
        mpg.setStrategy(strategy);

        mpg.execute();
    }
}
