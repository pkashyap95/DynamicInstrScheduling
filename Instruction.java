public class Instruction
{   
    //Original info
    public int instr_number;
    public int src_r1_reg;
    public int src_r2_reg;
    public int op_type;
    public int dst_reg;
    //rename info
    public int rn_r1_reg;
    public int rn_r2_reg;
    public int rn_r1_rdy;
    public int rn_r2_rdy;    
    public int rn_dst_reg;
    //
    public int stop_execute_time;
    //
    public int rob_dest;
    //Time stamps
    public int fe_time;
    public int de_time;
    public int rn_time;
    public int rr_time;
    public int di_time;
    public int is_time;
    public int ex_time;
    public int wb_time;
    public int rt_time;
    public Instruction(){}
    public void display_time(){
        System.out.println(instr_number+" fu{"+op_type+"} src{"+src_r1_reg+","+src_r2_reg+"} dst{"+dst_reg+"} FE{"+fe_time+"} DE{"+de_time+"} RN{"+rn_time+"} RR{"+rr_time+"} DI{"+di_time+"} IS{"+is_time+"} EX{"+ex_time+"} RT{"+rt_time+"}");
    }
    public void display_internal(){
        System.out.println(instr_number+" src{"+src_r1_reg+","+src_r2_reg+"} dst{"+dst_reg+"} rn_src{"+rn_r1_reg+","+rn_r2_reg+"} ready{"+rn_r1_rdy+","+rn_r2_rdy+"}");
    }
}