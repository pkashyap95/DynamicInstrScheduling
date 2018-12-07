import java.io.*;
import java.util.Objects;
import java.util.ArrayList;
/* args hold the command line arguments

    Example:-
    sim 256 32 4 gcc_trace.txt
    args[0] = "256"
    args[1] = "32"
    ... and so on
*/
public class sim
{
    public static void main(String[] args) {

        proc_params params = new proc_params();         // check proc_params.java class for the definition of class proc_params 
        String trace_file = "";                         // Variable that holds trace file name
        int op_type, dest, src1, src2;                  // Variable read from input file
        long pc;                                        // Variable holds the address read from input file

        if (args.length != 4)    // Checks if correct number of inputs have been given. Throw error and exit if wrong
        {
            System.out.println("Error: Wrong number of inputs:" + args.length);
            System.exit(0);
        }
        params.rob_size     = Long.parseLong(args[0]);
        params.iq_size      = Long.parseLong(args[1]);
        params.width        = Long.parseLong(args[2]);
        trace_file          = args[3];

        //System.out.printf("rob_size:%d iq_size:%d width:%d tracefile:%s%n", params.rob_size, params.iq_size, params.width, trace_file);    // Print and test if file is read correctly 
        try
        {   
            Pipeline test_pipeline = new Pipeline(params, trace_file);
            do{
                test_pipeline.retire();
                test_pipeline.writeback();
                test_pipeline.execute();
                test_pipeline.issue();
                test_pipeline.dispatch();
                test_pipeline.register_read();
                test_pipeline.rename();
                test_pipeline.decode();
                test_pipeline.fetch();
                test_pipeline.advanceTime();
                test_pipeline.checkIfPipelineEmpty();
            }
            while(!test_pipeline.advanceCycle());
            //test_pipeline.printContents();
            System.out.printf("# === Simulator Command =========\n");
            System.out.printf(".\\sim %d %d %d %s \n", params.rob_size, params.iq_size, params.width, trace_file);
            test_pipeline.displayConfiguration();
            test_pipeline.displaySimulationResults();                    
        }
        catch (IOException x)                                       // Throw error if file I/O fails
        {
            System.err.format("IOException: %s%n", x);
        }

    }
}
