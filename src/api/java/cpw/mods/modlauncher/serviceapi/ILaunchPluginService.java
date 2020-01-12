/*
 * ModLauncher - for launching Java programs with in-flight transformation ability.
 *
 *     Copyright (C) 2017-2019 cpw
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Lesser General Public License as published by
 *     the Free Software Foundation, version 3 of the License.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package cpw.mods.modlauncher.serviceapi;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Loaded from the initial classpath of the system to identify plugins that wish to work across the system.
 *
 * Mixin and AccessTransformer provide instances. Other plugins can also be added here, but their services are likely
 * not to be called upon.
 *
 */
public interface ILaunchPluginService {
    /**
     * The name of this plugin. Must be unique. Multiple plugins by the same name will result in a hard exit.
     *
     * Launcher and other systems will rely on this name to find services provided by this plugin, so it should be
     * stable.
     *
     * @return the name of the plugin
     */
    String name();

    /**
     * Indicates where the {@link #processClass(Phase, ClassNode, Type)} method should be called.
     */
    enum Phase {
        /**
         * Before regular transformer processing
         */
        BEFORE,
        /**
         * After regular transformer processing
         */
        AFTER
    }

    enum ComputeLevel {
        /**
         * This plugin did not change the class and therefor requires no rewrite of the class.
         * This is the fastest option
         */
        NO_REWRITE,
        /**
         * The plugin did change the class and requires a rewrite, but does not require any additional computation
         * as frames and maxs in the class did not change of have been corrected by the plugin
         */
        SIMPLE_REWRITE,
        /**
         * The plugin did change the class and requires a rewrite, and requires max re-computation,
         * but frames are unchanged or corrected by the plugin
         */
        COMPUTE_MAXS,
        /**
         * The plugin did change the class and requires a rewrite, and requires max and frame re-computation.
         * This is the slowest, but also safest method if you don't know what level is required
         */
        COMPUTE_FRAMES;

        public ComputeLevel mergeWith(ComputeLevel level) {
            if (level.ordinal() > this.ordinal())
                return level;
            else
                return this;
        }

        public int getFlag() {
            switch (this) {
                case NO_REWRITE:
                    throw new RuntimeException("No flag available for " + this.name() + "!");
                case SIMPLE_REWRITE:
                    return 0;
                case COMPUTE_MAXS:
                    return ClassWriter.COMPUTE_MAXS;
                case COMPUTE_FRAMES:
                    return ClassWriter.COMPUTE_FRAMES;
                default:
                    throw new RuntimeException("Unknown enum constant " + this);
            }
        }
    }

    /**
     * If this plugin wants to receive the {@link ClassNode} into {@link #processClass}
     * @param classType the class to consider
     * @param isEmpty if the class is empty at present (indicates no backing file found)
     * @return the set of Phases the plugin wishes to be called back with
     */
    EnumSet<Phase> handlesClass(Type classType, final boolean isEmpty);

    /**
     * If this plugin wants to receive the {@link ClassNode} into {@link #processClass}
     * @param classType the class to consider
     * @param isEmpty if the class is empty at present (indicates no backing file found)
     * @param reason Reason for transformation request. "classloading" or the name of an {@link ILaunchPluginService}
     * @return the set of Phases the plugin wishes to be called back with
     */
    default EnumSet<Phase> handlesClass(Type classType, final boolean isEmpty, final String reason) {
        return handlesClass(classType, isEmpty);
    }

    /**
     * Each class loaded is offered to the plugin for processing.
     * Ordering between plugins is not known.
     *
     * @param phase The phase of the supplied class node
     * @param classNode the classnode to process
     * @param classType the name of the class
     * @return the processed classnode
     */
    boolean processClass(final Phase phase, ClassNode classNode, final Type classType);

    /**
     * Each class loaded is offered to the plugin for processing.
     * Ordering between plugins is not known.
     *
     * @param phase The phase of the supplied class node
     * @param classNode the classnode to process
     * @param classType the name of the class
     * @param reason Reason for transformation. "classloading" or the name of an {@link ILaunchPluginService}
     * @return the processed classnode
     */
    default boolean processClass(final Phase phase, ClassNode classNode, final Type classType, String reason) {
        return processClass(phase, classNode, classType);
    }

    /**
     * Each class loaded is offered to the plugin for processing.
     * Ordering between plugins is not known.
     *
     * @param phase The phase of the supplied class node
     * @param classNode the classnode to process
     * @param classType the name of the class
     * @param reason Reason for transformation. "classloading" or the name of an {@link ILaunchPluginService}
     * @return The required compute level for this class
     */
    default ComputeLevel processClassNew(final Phase phase, ClassNode classNode, final Type classType, String reason) {
        return processClass(phase, classNode, classType, reason) ? ComputeLevel.COMPUTE_FRAMES : ComputeLevel.NO_REWRITE;
    }

    /**
     * Adds a resource to this plugin for processing by it. Minecraft will always be the only resource offered.
     * (Name will be "minecraft").
     *
     * @param resource The resource to be considered by this plugin.
     * @param name A name for this resource.
     */
    @Deprecated
    default void addResource(Path resource, String name) {}

    /**
     * Offer scan results from TransformationServices to this plugin.
     *
     * @param resources A collection of all the results
     */
    default void addResources(List<Map.Entry<String, Path>> resources) {}

    default void initializeLaunch(ITransformerLoader transformerLoader,  Path[] specialPaths) {}
    /**
     * Get a plugin specific extension object from the plugin. This can be used to expose proprietary interfaces
     * to Launchers without ModLauncher needing to understand them.
     *
     * @param <T> The type of the extension
     * @return An extension object
     */
    default <T> T getExtension() {return null;}

    /**
     * Receives a call immediately after handlesClass for any transformer that declares an interest.
     *
     * the consumer can be called repeatedly to generate new AuditTrail entries in the audit log.
     *
     * @param className className that is being transformed
     * @param auditDataAcceptor accepts an array of strings to add a new audit trail record with the data
     */
    default void customAuditConsumer(String className, Consumer<String[]> auditDataAcceptor) {
    }

    interface ITransformerLoader {
        byte[] buildTransformedClassNodeFor(final String className) throws ClassNotFoundException;
    }
}
