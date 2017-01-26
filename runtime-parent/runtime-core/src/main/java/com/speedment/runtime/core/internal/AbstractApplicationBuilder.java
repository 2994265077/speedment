/**
 *
 * Copyright (c) 2006-2017, Speedment, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); You may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.speedment.runtime.core.internal;

import com.speedment.common.injector.InjectBundle;
import com.speedment.common.injector.Injector;
import com.speedment.common.injector.exception.CyclicReferenceException;
import com.speedment.common.injector.internal.InjectorImpl;
import static com.speedment.common.invariant.NullUtil.requireNonNulls;
import com.speedment.common.logger.Level;
import com.speedment.common.logger.Logger;
import com.speedment.common.logger.LoggerManager;
import com.speedment.common.tuple.Tuple2;
import com.speedment.common.tuple.Tuple3;
import com.speedment.common.tuple.Tuples;
import com.speedment.runtime.config.Dbms;
import com.speedment.runtime.config.Document;
import com.speedment.runtime.config.Project;
import com.speedment.runtime.config.Schema;
import com.speedment.runtime.config.trait.HasEnabled;
import com.speedment.runtime.config.trait.HasName;
import com.speedment.runtime.config.util.DocumentDbUtil;
import static com.speedment.runtime.config.util.DocumentUtil.Name.DATABASE_NAME;
import static com.speedment.runtime.config.util.DocumentUtil.relativeName;
import com.speedment.runtime.core.ApplicationBuilder;
import com.speedment.runtime.core.ApplicationMetadata;
import com.speedment.runtime.core.RuntimeBundle;
import com.speedment.runtime.core.Speedment;
import com.speedment.runtime.core.component.DbmsHandlerComponent;
import com.speedment.runtime.core.component.InfoComponent;
import com.speedment.runtime.core.component.PasswordComponent;
import com.speedment.runtime.core.component.ProjectComponent;
import com.speedment.runtime.core.db.DbmsMetadataHandler;
import com.speedment.runtime.core.db.DbmsType;
import com.speedment.runtime.core.exception.SpeedmentException;
import com.speedment.runtime.core.manager.Manager;
import com.speedment.runtime.core.util.DatabaseUtil;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import static java.util.Objects.requireNonNull;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This abstract class is implemented by classes that can build a
 * {@link Speedment} application.
 *
 * @param <APP> the type that is being built
 * @param <BUILDER> the (self) type of the AbstractApplicationBuilder
 *
 * @author Per Minborg
 * @author Emil Forslund
 * @since 2.0.0
 */
public abstract class AbstractApplicationBuilder<
        APP extends Speedment, BUILDER extends AbstractApplicationBuilder<APP, BUILDER>> implements ApplicationBuilder<APP, BUILDER> {

    private final static Logger LOGGER = LoggerManager.getLogger(LogType.APPLICATION_BUILDER.getLoggerName());

    private final List<Tuple3<Class<? extends Document>, String, BiConsumer<Injector, ? extends Document>>> withsNamed;
    private final List<Tuple2<Class<? extends Document>, BiConsumer<Injector, ? extends Document>>> withsAll;
    private final Injector.Builder injector;

    private boolean skipCheckDatabaseConnectivity;
    private boolean skipValidateRuntimeConfig;
    private boolean skipLogoPrintout;
    
    protected AbstractApplicationBuilder(
        Class<? extends APP> applicationImplClass,
        Class<? extends ApplicationMetadata> metadataClass) {

        this(Injector.builder()
            .putInBundle(RuntimeBundle.class)
            .put(applicationImplClass)
            .put(metadataClass)
        );
    }
    
    protected AbstractApplicationBuilder(
        ClassLoader classLoader,
        Class<? extends APP> applicationImplClass,
        Class<? extends ApplicationMetadata> metadataClass) {

        this(Injector.builder(classLoader)
            .putInBundle(RuntimeBundle.class)
            .put(applicationImplClass)
            .put(metadataClass)
        );
    }

    protected AbstractApplicationBuilder(Injector.Builder injector) {
        this.injector   = requireNonNull(injector);
        this.withsNamed = new ArrayList<>();
        this.withsAll   = new ArrayList<>();
        this.skipCheckDatabaseConnectivity = false;
        this.skipValidateRuntimeConfig     = false;
    }

    @Override
    public <C extends Document & HasEnabled> BUILDER with(Class<C> type, String name, BiConsumer<Injector, C> consumer) {
        requireNonNulls(type, name, consumer);
        withsNamed.add(Tuples.of(type, name, consumer));
        return self();
    }

    @Override
    public <C extends Document & HasEnabled> BUILDER with(Class<C> type, BiConsumer<Injector, C> consumer) {
        requireNonNulls(type, consumer);
        withsAll.add(Tuples.of(type, consumer));
        return self();
    }

    @Override
    public BUILDER withParam(String key, String value) {
        requireNonNulls(key, value);
        injector.putParam(key, value);
        return self();
    }

    @Override
    public BUILDER withPassword(char[] password) {
        // password nullable
        with(Dbms.class, (inj, dbms) -> inj.getOrThrow(PasswordComponent.class).put(dbms, password));
        return self();
    }

    @Override
    public BUILDER withPassword(String dbmsName, char[] password) {
        requireNonNull(dbmsName);
        // password nullable
        with(Dbms.class, dbmsName, (inj, dbms) -> inj.getOrThrow(PasswordComponent.class).put(dbms, password));
        return self();
    }

    @Override
    public BUILDER withPassword(String password) {
        // password nullable
        return withPassword(password == null ? null : password.toCharArray());
    }

    @Override
    public BUILDER withPassword(String dbmsName, String password) {
        requireNonNull(dbmsName);
        // password nullable
        return withPassword(dbmsName, password == null ? null : password.toCharArray());
    }

    @Override
    public BUILDER withUsername(String username) {
        // username nullable
        with(Dbms.class, dbms -> dbms.mutator().setUsername(username));
        return self();
    }

    @Override
    public BUILDER withUsername(String dbmsName, String username) {
        requireNonNull(dbmsName);
        // username nullable
        with(Dbms.class, dbmsName, d -> d.mutator().setUsername(username));
        return self();
    }

    @Override
    public BUILDER withIpAddress(String ipAddress) {
        requireNonNull(ipAddress);
        with(Dbms.class, d -> d.mutator().setIpAddress(ipAddress));
        return self();
    }

    @Override
    public BUILDER withIpAddress(String dbmsName, String ipAddress) {
        requireNonNulls(dbmsName, ipAddress);
        with(Dbms.class, dbmsName, d -> d.mutator().setIpAddress(ipAddress));
        return self();
    }

    @Override
    public BUILDER withPort(int port) {
        with(Dbms.class, d -> d.mutator().setPort(port));
        return self();
    }

    @Override
    public BUILDER withPort(String dbmsName, int port) {
        requireNonNull(dbmsName);
        with(Dbms.class, dbmsName, d -> d.mutator().setPort(port));
        return self();
    }

    @Override
    public BUILDER withSchema(String schemaName) {
        requireNonNull(schemaName);
        with(Schema.class, s -> s.mutator().setName(schemaName));
        return self();
    }

    @Override
    public BUILDER withSchema(String oldSchemaName, String schemaName) {
        requireNonNulls(oldSchemaName, schemaName);
        with(Schema.class, oldSchemaName, s -> s.mutator().setName(schemaName));
        return self();
    }

    @Override
    public BUILDER withConnectionUrl(String connectionUrl) {
        with(Dbms.class, d -> d.mutator().setConnectionUrl(connectionUrl));
        return self();
    }

    @Override
    public BUILDER withConnectionUrl(String dbmsName, String connectionUrl) {
        requireNonNull(dbmsName);
        with(Dbms.class, dbmsName, s -> s.mutator().setName(connectionUrl));
        return self();
    }

    @Override
    public <M extends Manager<?>> BUILDER withManager(Class<M> managerImplType) {
        requireNonNull(managerImplType);
        withInjectable(injector, managerImplType, M::getEntityClass);
        return self();
    }

    @Override
    public BUILDER withSkipCheckDatabaseConnectivity() {
        this.skipCheckDatabaseConnectivity = true;
        return self();
    }

    @Override
    public BUILDER withSkipValidateRuntimeConfig() {
        this.skipValidateRuntimeConfig = true;
        return self();
    }

    @Override
    public BUILDER withSkipLogoPrintout() {
        this.skipLogoPrintout = true;
        return self();
    }

    @Override
    public BUILDER withBundle(Class<? extends InjectBundle> bundleClass) {
        requireNonNull(bundleClass);
        injector.putInBundle(bundleClass);
        return self();
    }

    @Override
    public BUILDER withComponent(Class<?> injectableClass) {
        requireNonNull(injectableClass);
        injector.put(injectableClass);
        return self();
    }

    @Override
    public BUILDER withComponent(String key, Class<?> injectableClass) {
        requireNonNulls(key, injectableClass);
        injector.put(key, injectableClass);
        return self();
    }

    @Override
    public BUILDER withLogging(HasLogglerName namer) {
        LoggerManager.getLogger(namer.getLoggerName()).setLevel(Level.DEBUG);
        if (LogType.APPLICATION_BUILDER.getLoggerName().equals(namer.getLoggerName())) {
            LoggerManager.getLogger(InjectorImpl.class).setLevel(Level.DEBUG); // Special case becaues its in a common module
        }
        return self();
    }

    @Override
    public final APP build() {
        final Injector inj;

        try {
            inj = injector.build();
        } catch (final InstantiationException | CyclicReferenceException ex) {
            throw new SpeedmentException("Error in dependency injection.", ex);
        }

        loadAndSetProject(inj);

        printWelcomeMessage(inj);

        if (!skipValidateRuntimeConfig) {
            validateRuntimeConfig(inj);
        }
        if (!skipCheckDatabaseConnectivity) {
            checkDatabaseConnectivity(inj);
        }

        return build(inj);
    }

    /**
     * Builds the application using the specified injector.
     *
     * @param injector the injector to use
     * @return the built instance.
     */
    protected abstract APP build(Injector injector);

    /**
     * Builds up the complete Project meta data tree.
     *
     * @param injector the injector to use
     */
    protected void loadAndSetProject(Injector injector) {
        LOGGER.debug("Loading and Setting Project Configuration");
        final Project project = injector.getOrThrow(ProjectComponent.class).getProject();

        // Apply overidden item (if any) for all Documents of a given class
        withsAll.forEach(t2 -> {
            final Class<? extends Document> clazz = t2.get0();

            @SuppressWarnings("unchecked")
            final BiConsumer<Injector, Document> consumer
                = (BiConsumer<Injector, Document>) t2.get1();

            DocumentDbUtil.traverseOver(project)
                .filter(clazz::isInstance)
                .map(Document.class::cast)
                .forEachOrdered(doc -> consumer.accept(injector, doc));
        });

        // Apply a named overidden item (if any) for all Entities of a given class
        withsNamed.forEach(t3 -> {
            final Class<? extends Document> clazz = t3.get0();
            final String name = t3.get1();

            @SuppressWarnings("unchecked")
            final BiConsumer<Injector, Document> consumer
                = (BiConsumer<Injector, Document>) t3.get2();

                                   
            /* This is the old naming convention "proj.dbms.schema.table.colum". Todo: Remove in next API version */
            DocumentDbUtil.traverseOver(project)
                .filter(clazz::isInstance)
                .filter(HasName.class::isInstance)
                .map(HasName.class::cast)
                .filter(c -> name.equals(relativeName(c, Project.class, DATABASE_NAME)))
                .forEachOrdered(doc -> consumer.accept(injector, doc));

            /* New naming convention "dbms.schema.table.column"*/
            DocumentDbUtil.traverseOver(project)
                .filter(clazz::isInstance)
                .filter(HasName.class::isInstance)
                .map(HasName.class::cast)
                .filter(c -> name.equals(relativeName(c, Dbms.class, DATABASE_NAME))) // Now relative to DBMS!
                .forEachOrdered(doc -> consumer.accept(injector, doc));

        });
    }

    protected void validateRuntimeConfig(Injector injector) {
        LOGGER.debug("Validating Runtime Configuration");
        final Project project = injector.getOrThrow(ProjectComponent.class).getProject();
        if (project == null) {
            throw new SpeedmentException("No project defined");
        }

        project.dbmses().forEach(d -> {
            final String typeName = d.getTypeName();
            final Optional<DbmsType> oDbmsType = injector.getOrThrow(
                DbmsHandlerComponent.class).findByName(typeName);
            
            if (!oDbmsType.isPresent()) {
                throw new SpeedmentException("The database type " + typeName + 
                    " is not registered with the " + 
                    DbmsHandlerComponent.class.getSimpleName());
            }
            
            final String driverName = oDbmsType.get().getDriverName();
            try {
                Class.forName(driverName);
            } catch (final ClassNotFoundException cnfe) {
                LOGGER.error(cnfe, "The database driver class " + driverName + 
                    " is not available. Make sure to include it in your " + 
                    "class path (e.g. in the POM file)"
                );
            }
        });

    }

    protected void checkDatabaseConnectivity(Injector injector) {
        LOGGER.debug("Checking Database Connectivity");
        final Project project = injector.getOrThrow(ProjectComponent.class).getProject();
        project.dbmses().forEachOrdered(dbms -> {

            final DbmsHandlerComponent dbmsHandlerComponent = injector.getOrThrow(DbmsHandlerComponent.class);
            final DbmsType dbmsType = DatabaseUtil.dbmsTypeOf(dbmsHandlerComponent, dbms);
            final DbmsMetadataHandler handler = dbmsType.getMetadataHandler();

            try {
                LOGGER.info(handler.getDbmsInfoString(dbms));
            } catch (final SQLException sqle) {
                throw new SpeedmentException("Unable to establish initial " + 
                    "connection with the database named " + 
                    dbms.getName() + ".", sqle);
            }
        });
    }

    /**
     * Prints a welcome message to the output channel.
     *
     * @param injector the injector to use
     */
    protected void printWelcomeMessage(Injector injector) {

        final InfoComponent info = injector.getOrThrow(InfoComponent.class);
        final String title = info.getTitle();
        final String version = info.getImplementationVersion();

        if (!skipLogoPrintout) {
            final String speedmentMsg = "\n"
                + "   ____                   _                     _     \n"
                + "  / ___'_ __  __  __   __| |_ __ __    __ _ __ | |    \n"
                + "  \\___ | '_ |/  \\/  \\ / _  | '_ \\ _ \\ /  \\ '_ \\| |_   \n"
                + "   ___)| |_)| '_/ '_/| (_| | | | | | | '_/ | | |  _|  \n"
                + "  |____| .__|\\__\\\\__\\ \\____|_| |_| |_|\\__\\_| |_| '_   \n"
                + "=======|_|======================================\\__|==\n"
                + "   :: " + title + " by " + info.getVendor()
                + ":: (v" + version + ") \n";

            LOGGER.info(speedmentMsg);
        }
        final String msg = title + " (" + info.getSubtitle()
            + ") version " + version
            + " by " + info.getVendor()
            + " Specification version "
            + info.getSpecificationVersion();
        LOGGER.info(msg);

        if (!info.isProductionMode()) {
            LOGGER.warn("This version is NOT INTENDED FOR PRODUCTION USE!");
        }

        try {
            final Package package_ = Runtime.class.getPackage();
            final String javaMsg = package_.getSpecificationTitle()
                + " " + package_.getSpecificationVersion()
                + " by " + package_.getSpecificationVendor()
                + ". Implementation "
                + package_.getImplementationVendor()
                + " " + package_.getImplementationVersion()
                + " by " + package_.getImplementationVendor();
            LOGGER.info(javaMsg);

            final String versionString = package_.getImplementationVersion();
            final Optional<Boolean> isVersionOk = isVersionOk(versionString);
            if (isVersionOk.isPresent()) {
                if (!isVersionOk.get()) {
                    LOGGER.warn("The current Java version (" + versionString + 
                        ") is outdated. Please upgrade to a more recent " + 
                        "Java version.");
                }
            } else {
                LOGGER.warn("Unable to fully parse the java version. " + 
                    "Version check skipped!");
            }
        } catch (final Exception ex) {
            LOGGER.info("Unknown Java version.");
        }

    }

    private BUILDER self() {
        @SuppressWarnings("unchecked")
        final BUILDER builder = (BUILDER) this;
        return builder;
    }

    Optional<Boolean> isVersionOk(String versionString) {
        final Pattern pattern = Pattern.compile("(\\d+)[\\\\.](\\d+)[\\\\.](\\d+)_(\\d+)");
        final Matcher matcher = pattern.matcher(versionString);

        if (matcher.find() && matcher.groupCount() >= 4) {
            final String majorVersionString = matcher.group(1);
            final String minorVersionString = matcher.group(2);
            final String patchVersionString = matcher.group(3);
            final String microVersionString = matcher.group(4);
            try {
                final int majorVersion = Integer.parseInt(majorVersionString);
                final int minorVersion = Integer.parseInt(minorVersionString);
                final int patchVersion = Integer.parseInt(patchVersionString);
                final int microVersion = Integer.parseInt(microVersionString);
                boolean ok = true;
                if (majorVersion < 1) {
                    ok = false;
                }
                if (majorVersion == 1) {
                    if (minorVersion < 8) {
                        ok = false;
                    }
                    if (minorVersion == 8) {
                        if (patchVersion < 0) {
                            ok = false;
                        }
                        if (patchVersion == 0) {
                            if (microVersion < 40) {
                                ok = false;
                            }
                        }
                    }
                }
                return Optional.of(ok);
            } catch (NumberFormatException nfe) {
                return Optional.empty();
            }
        } else {
            return Optional.empty();
        }
    }

    private static <T> void withInjectable(
            Injector.Builder injector, 
            Class<T> injectableImplType, 
            Function<T, Class<?>> keyExtractor) {
        
        requireNonNull(injectableImplType);

        final T injectable;
        try {
            final Constructor<T> constructor = injectableImplType.getDeclaredConstructor();
            constructor.setAccessible(true);
            injectable = constructor.newInstance();
        } catch (final InstantiationException 
                     | IllegalAccessException 
                     | NoSuchMethodException 
                     | InvocationTargetException ex) {

            throw new SpeedmentException(ex);
        }

        final Class<?> key = keyExtractor.apply(injectable);
        injector.put(key.getName(), injectableImplType);
    }
}
