import java.io.*;
import java.util.Objects;

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

        System.out.printf("rob_size:%d iq_size:%d width:%d tracefile:%s%n", params.rob_size, params.iq_size, params.width, trace_file);    // Print and test if file is read correctly 
        // Read file line by line
        try (BufferedReader br = new BufferedReader(new FileReader(trace_file)))
        {
            String line;
            while ((line = br.readLine()) != null) {
                String [] split = line.split("\\s+");               // split line at whitespace
                pc      = Long.parseLong(split[0], 16);             // gets address from String split and converts to long. parseLong uses base 16
                op_type = Integer.parseInt(split[1], 10);
                dest    = Integer.parseInt(split[2], 10);
                src1    = Integer.parseInt(split[3], 10);
                src2    = Integer.parseInt(split[4], 10);
                
                System.out.printf("%x %d %d %d %d%n", pc, op_type, dest, src1, src2);    // Print and test if file is read correctly 
                /* ************************************
                  Add calls to OOO simulator code here
                **************************************/
            }
        }
        catch (IOException x)                                       // Throw error if file I/O fails
        {
            System.err.format("IOException: %s%n", x);
        }
    }
}
