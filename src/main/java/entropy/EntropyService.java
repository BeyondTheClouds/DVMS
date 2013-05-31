package entropy;

import dvms.dvms.DvmsModel.*;
import dvms.log.Logger;
import dvms.monitor.LibvirtMonitorDriver;
import dvms.scheduling.ComputingState;
import entropy.configuration.Configuration;
import entropy.configuration.SimpleManagedElementSet;
import entropy.execution.Dependencies;
import entropy.execution.TimedExecutionGraph;
import entropy.plan.PlanException;
import entropy.plan.TimedReconfigurationPlan;
import entropy.plan.action.Action;
import entropy.plan.action.Migration;
import entropy.plan.choco.ChocoCustomRP;
import entropy.plan.durationEvaluator.MockDurationEvaluator;
import entropy.vjob.DefaultVJob;
import entropy.vjob.VJob;
import org.bbk.driver.Node;
import org.bbk.driver.VirtualMachine;

import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: jonathan
 * Date: 5/21/13
 * Time: 5:26 PM
 * To change this template use File | Settings | File Templates.
 */
public class EntropyService {

    private static EntropyService instance = null;

    private ChocoCustomRP planner = null;

    private EntropyService() {
        planner = new ChocoCustomRP(new MockDurationEvaluator(2, 5, 1, 1, 7, 14, 7, 2, 4));
        planner.setTimeLimit(2);
    }

    public static EntropyService getInstance() {
        if (instance == null) {
            instance = new EntropyService();
        }

        return instance;
    }

    public ChocoCustomRP getPlanner() {
        return planner;
    }

    public static boolean computeAndApplyReconfigurationPlan(Configuration configuration, List<PhysicalNode> machines) {

        ComputingState res = ComputingState.VMRP_SUCCESS;

        List<VJob> vjobs = new ArrayList<VJob>();
        DefaultVJob v = new DefaultVJob("v1");

        v.addVirtualMachines(configuration.getRunnings());
        vjobs.add(v);

        TimedReconfigurationPlan reconfigurationPlan = null;

        try {
            reconfigurationPlan = getInstance().getPlanner().compute(configuration,
                    configuration.getRunnings(),
                    configuration.getWaitings(),
                    configuration.getSleepings(),
                    new SimpleManagedElementSet(),
                    configuration.getOnlines(),
                    configuration.getOfflines(),
                    vjobs
            );
        } catch (PlanException e) {
//            e.printStackTrace();
            Logger.log("Entropy: No solution :(");
            res = ComputingState.VMRP_FAILED;
        }


        int reconfigurationPlanCost = 0;
        Configuration newConfiguration = null;
        int nbMigrations = 0;
        int reconfigurationGraphDepth = 0;

        if (reconfigurationPlan != null) {
            if (reconfigurationPlan.getActions().isEmpty())
                res = ComputingState.NO_RECONFIGURATION_NEEDED;

            reconfigurationPlanCost = reconfigurationPlan.getDuration();
            newConfiguration = reconfigurationPlan.getDestination();
            nbMigrations = computeNbMigrations(reconfigurationPlan, machines);
            reconfigurationGraphDepth = computeReconfigurationGraphDepth(reconfigurationPlan, machines);


            try {
                applyReconfigurationPlanLogically(reconfigurationPlan, configuration, machines);

            } catch (Exception e) {

                e.printStackTrace();
            }

        }

        return res != ComputingState.VMRP_FAILED;
    }

    //Get the number of migrations
    private static int computeNbMigrations(TimedReconfigurationPlan reconfigurationPlan, List<PhysicalNode> machines) {
        int nbMigrations = 0;

        for (Action a : reconfigurationPlan.getActions()) {
            if (a instanceof Migration) {
                nbMigrations++;
            }
        }

        return nbMigrations;
    }

    //Get the depth of the reconfiguration graph
    //May be compared to the number of steps in Entropy 1.1.1
    //Return 0 if there is no action, and (1 + maximum number of dependencies) otherwise
    private static int computeReconfigurationGraphDepth(TimedReconfigurationPlan reconfigurationPlan, List<PhysicalNode> machines) {
        if (reconfigurationPlan.getActions().isEmpty()) {
            return 0;
        } else {
            int maxNbDeps = 0;
            TimedExecutionGraph g = reconfigurationPlan.extractExecutionGraph();
            int nbDeps;

            //Set the reverse dependencies map
            for (Dependencies dep : g.extractDependencies()) {
                nbDeps = dep.getUnsatisfiedDependencies().size();

                if (nbDeps > maxNbDeps)
                    maxNbDeps = nbDeps;
            }

            return 1 + maxNbDeps;
        }
    }

    //Apply the reconfiguration plan logically (i.e. create/delete Java objects)
    private static void applyReconfigurationPlanLogically(TimedReconfigurationPlan reconfigurationPlan, Configuration conf, List<PhysicalNode> machines) throws InterruptedException {
        Map<Action, List<Dependencies>> revDependencies = new HashMap<Action, List<Dependencies>>();
        TimedExecutionGraph g = reconfigurationPlan.extractExecutionGraph();

        //Set the reverse dependencies map
        for (Dependencies dep : g.extractDependencies()) {
            for (Action a : dep.getUnsatisfiedDependencies()) {
                if (!revDependencies.containsKey(a)) {
                    revDependencies.put(a, new LinkedList<Dependencies>());
                }
                revDependencies.get(a).add(dep);
            }
        }

        //Start the feasible actions
        // ie, actions with a start moment equals to 0.
        for (Action a : reconfigurationPlan) {
            if (a.getStartMoment() == 0) {
                instantiateAndStart(a, conf, machines);
            }

            if (revDependencies.containsKey(a)) {
                //Get the associated depenencies and update it
                for (Dependencies dep : revDependencies.get(a)) {
                    dep.removeDependency(a);
                    //Launch new feasible actions.
                    if (dep.isFeasible()) {
                        instantiateAndStart(dep.getAction(), conf, machines);
                    }
                }
            }
        }
    }

    private static void instantiateAndStart(Action a, Configuration conf, List<PhysicalNode> machines) throws InterruptedException {

        if (a instanceof Migration) {
            Migration migration = (Migration) a;


            for(PhysicalNode machine: machines) {

                // looking for the destination node
                if(machine.ref().toString().equals(migration.getDestination().toString())) {
                    // we found the destination node, now we have to found the good virtualMachine

                    Iterable<VirtualMachine> iterable = (Iterable<VirtualMachine>) machine.machines().toIterable();

                    for(VirtualMachine vm : iterable) {
                        if(vm.getName().equals(migration.getVirtualMachine().getName())) {
                            LibvirtMonitorDriver.driver().migrate(vm, new Node(machine.url()));
                        }
                    }
                }
            }


        } else {
            Logger.log("UNRECOGNIZED ACTION WHEN APPLYING THE RECONFIGURATION PLAN");
        }
    }
}
