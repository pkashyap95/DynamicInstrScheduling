/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author Priyank Kashyap
 */
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.io.*;
import java.util.LinkedList; 
import java.util.Queue; 


public class Pipeline {
    private final int numberOfARFRegs=67;
    private final int mWidth;
    private final int mROB_Size;
    private final int mIQ_Size;
    private int mSimulatorTime;
    private int mDynamicInstrCount;
    private int mElementsInROB;
    private boolean trace_file_empty;
    private BufferedReader br;
    private boolean mPipelineEmpty;
    private Map<Integer,rename_table_entry> rename_map_table;
    private reorder_buffer_entry [] mROB;
    private int mROB_Head, mROB_Tail;
    private ArrayList <Instruction> DE,RN,RR,DI, IQ, execute_list, WB;
    public Pipeline(proc_params cpu_config, String trace_file) throws FileNotFoundException{
        //Initalize internal variables
        mWidth    = (int) cpu_config.width;
        mROB_Size = (int) cpu_config.rob_size;
        mIQ_Size  = (int) cpu_config.iq_size;
        mSimulatorTime = 0;
        mDynamicInstrCount = 0;
        trace_file_empty = false;
        
        br = new BufferedReader(new FileReader(trace_file));
        //Initalize pipeline registers
        DE = new ArrayList<Instruction>();
        RN = new ArrayList<Instruction>();
        RR = new ArrayList<Instruction>();
        DI = new ArrayList<Instruction>();
        IQ = new ArrayList<Instruction>();
        WB = new ArrayList<Instruction>();
        execute_list = new ArrayList<Instruction>();
        reorder_buffer_entry empty =new reorder_buffer_entry();
        empty.dst =0;
        empty.ready_bit = -1;
        empty.valid =0;
        empty.instr_number =-1;
        mROB = new reorder_buffer_entry[mROB_Size];
        for(int i=0; i<mROB_Size; i++){
            mROB[i]=empty;
        }
        mElementsInROB=0;
        mROB_Head=0;
        mROB_Tail=0;
        rename_map_table = new HashMap();
        for(int i = 0;i<numberOfARFRegs; i++){
            rename_map_table.put(Integer.valueOf(i),null);
        }
    }
    
    public void fetch() throws IOException{
        //System.out.println("Pipeline.fetch()");
        if(DE.size()==0){
            for (int i=0; i<mWidth; i++){ 
                String line=br.readLine();           
                Instruction temp = new Instruction();
                int time_in_fetch=1;
                int op_type ,dest, src1, src2;                       // Variable read from input file
                if(line != null){
                    String [] split   = line.split("\\s+");               // split line at whitespace
                    long pc           = Long.parseLong(split[0], 16);             // gets address from String split and converts to long. parseLong uses base 16
                    //System.out.printf("Input string %s: ", line);
                    temp.instr_number = mDynamicInstrCount;
                    temp.op_type      = Integer.parseInt(split[1], 10);
                    temp.dst_reg      = Integer.parseInt(split[2], 10);
                    temp.src_r1_reg   = Integer.parseInt(split[3], 10);
                    temp.src_r2_reg   = Integer.parseInt(split[4], 10);
                    temp.fe_time      = mSimulatorTime;
                    temp.de_time      = 0;
                    temp.rn_time      = 0;
                    temp.rt_time      = 0;
                    DE.add(temp);
                    mDynamicInstrCount++;
                    this.advanceCycle();
                }
                else{
                    trace_file_empty = true;
                }
            }
        }        
    }
    
    public void decode(){
        //
        //System.out.printf("Sim Time %d ", mSimulatorTime);
        if(DE.size()>0){
            for(Instruction i:DE){
                if(i != null){
                    if(i.de_time == 0){
                        i.de_time = mSimulatorTime;
                    }
                }
            }
            if(RN.size() == 0){
                for(int i= mWidth-1; i >= 0; i--){                    
                    Instruction removeFromDE= DE.remove(i);
                    RN.add(removeFromDE);
                    
                }
            }
        }
    }
    
    public void rename(){
        //System.out.println("Pipeline.rename()");
        if(RN.size()>0){ //there is a RN bundle
            for(Instruction i:RN){
                if(i != null){
                    if(i.rn_time == 0){
                        i.rn_time = mSimulatorTime;
                    }
                }
            }
            if((RR.size()== 0) && (mElementsInROB+RN.size())< mROB_Size){ //if RR is empty and there is space in the ROB to accept the RN bundle
                for(int i= mWidth-1; i >= 0; i--){                    
                    Instruction renameInstr= RN.remove(i);
                    allocateROB(renameInstr);
                    
                    RR.add(renameInstr);
                }
            }
        }
    }

    public void register_read(){
        if(RR.size()>0){ 
            for(Instruction i:RR){
                if(i != null){
                    if(i.rr_time == 0){
                        i.rr_time = mSimulatorTime;
                    }
                }
            }
            if((DI.size()== 0)){ 
                for(int i= mWidth-1; i >= 0; i--){
                    Instruction rdyInstr= RR.remove(i);
                    instructionReady(rdyInstr);
                    DI.add(rdyInstr);
                }
            }
        }
    }    

    public void dispatch(){
        if(DI.size()>0){
            for(Instruction i:DI){
                if(i != null){
                    if(i.di_time == 0){
                        i.di_time = mSimulatorTime;
                    }
                }
            }
            if((mIQ_Size- IQ.size()) >= DI.size()){
                for(int i= mWidth-1; i >= 0; i--){
                    Instruction renameInstr= DI.remove(i);
                    IQ.add(renameInstr);
                }
            }
        }
    }

    public void issue(){
        System.out.println("/////////////////////////////////////////");
//        for(Instruction e: IQ){            
//            System.out.println("IQ: "+ e.instr_number +" rn_src{"+e.rn_r1_reg+","+e.rn_r2_reg+"} ready{"+e.rn_r1_rdy+","+e.rn_r2_rdy+"}");
//        }
        int mRemove=0;
        if(IQ.size()>0){ 
            for(Instruction i:IQ){
                if(i != null){
                    if(i.is_time == 0){
                        i.is_time = mSimulatorTime;
                    }
                }
            }
            //Sort IQ
            for(int i = 0; i <IQ.size()-1; i++){
                int oldest_instr = i;
                for(int j=i+1; j<IQ.size();j++){
                    if(IQ.get(j).instr_number <IQ.get(i).instr_number){
                        oldest_instr = j;
                    }
                }
                Instruction temp = IQ.get(oldest_instr); 
                IQ.set(oldest_instr, IQ.get(i)); 
                IQ.set(i, temp); 
            }
            for(int i = 0; i <IQ.size(); i++){
                if(IQ.get(i).rn_r1_rdy==1 && IQ.get(i).rn_r2_rdy==1){
                    Instruction execInstr = IQ.remove(i);
                    execute_list.add(execInstr);
                }
            }
        }
    }    
    
    public void execute(){                
//        for(Instruction e: execute_list){            
//            System.out.println(mSimulatorTime + " EX: "+ e.instr_number +" rn_src{"+e.rn_r1_reg+","+e.rn_r2_reg+"} ready{"+e.rn_r1_rdy+","+e.rn_r2_rdy+"} "+e.stop_execute_time);
//        };
        if(execute_list.size()>0){
            for(Instruction i:execute_list){
                if(i != null){
                    if(i.ex_time == 0){
                        i.ex_time = mSimulatorTime;
                        if(i.op_type==0){
                            i.stop_execute_time=i.ex_time+1;
                        }
                        else if(i.op_type==1){
                            i.stop_execute_time=i.ex_time+2;
                        }
                        else if(i.op_type==2){
                            i.stop_execute_time=i.ex_time+5;
                        }
                    }
                }
            }
            for(int i= execute_list.size()-1; i >= 0; i--){
                if(execute_list.get(i).stop_execute_time==mSimulatorTime){
                    Instruction executed= execute_list.remove(i);
                    wakeup(executed);
                    WB.add(executed);
                }               
            }
        }
    } 
    public void writeback(){
        if(WB.size()>0){
            for(Instruction i:WB){
                if(i != null){
                    if(i.wb_time == 0){
                        i.wb_time = mSimulatorTime;
                    }
                }
            }
            for(int i = 0; i <WB.size()-1; i++){
                int oldest_instr = i;
                for(int j=i+1; j<WB.size();j++){
                    if(WB.get(j).instr_number <WB.get(i).instr_number){
                        oldest_instr = j;
                    }
                }
                Instruction temp = WB.get(oldest_instr); 
                WB.set(oldest_instr, WB.get(i)); 
                WB.set(i, temp); 
            }
            for(int i= 0; i <= WB.size()-1; i++){
//              Instruction removeFromWB= WB.remove(i);                                 //Instruction to be removed
                Instruction set_instr_rdy = WB.get(i);
                if(set_instr_rdy!= null) set_rob_ready(WB.get(i));
            }
        }
    }   
    
    public void retire(){
//        for(Instruction e: WB){            
//            System.out.println(mSimulatorTime + " RT: "+ e.instr_number +" rn_src{"+e.rn_r1_reg+","+e.rn_r2_reg+"} ready{"+e.rn_r1_rdy+","+e.rn_r2_rdy+"} "+e.stop_execute_time);
//        };
        if(WB.size()>0){
            int mRetireCOunter=0;
            for(Instruction i:WB){
                if(i != null){
                    if(i.rt_time == 0){
                        i.rt_time = mSimulatorTime;
                    }
                }
            }
            for(int i= 0; i < WB.size(); i++){
                Instruction toRetire = WB.get(i);                
                int status = remove_rob(toRetire.instr_number);
                System.out.println(mSimulatorTime + " RT: "+ toRetire.instr_number +" ready{"+toRetire.rn_r1_rdy+","+toRetire.rn_r2_rdy+"} ROB_Stat: "+ status+ " Instr_dst: "+ toRetire.dst_reg + " ROB_DST: "+ toRetire.rob_dest);
                if(status < 0) break;
                else {
                    WB.remove(i);
                    if(toRetire != null) toRetire.display_time();
                }
            }
        }
    }    
    ///////////////////////////////////////////////////////////////////////////
    public void instructionReady(Instruction readyInstr){
        if( readyInstr.rn_r1_reg ==-1 || readyInstr.rn_r1_reg ==-55){      //both immediate values or both arf values
            readyInstr.rn_r1_rdy = 1;
        }
        else{
            readyInstr.rn_r1_rdy = mROB[readyInstr.rn_r1_reg].ready_bit;
        }
        if( readyInstr.rn_r2_reg ==-1 || readyInstr.rn_r2_reg ==-55){      //both immediate values or both arf values
            readyInstr.rn_r2_rdy = 1;
        }
        else{
            readyInstr.rn_r2_rdy = mROB[readyInstr.rn_r2_reg].ready_bit;
        }
    }
    
    public void wakeup(Instruction execInstr){
        if(execInstr.dst_reg != 1){
            for(Instruction e: RR){
                if(e.rn_r1_reg==execInstr.rob_dest){
                    e.rn_r1_rdy =1;
                }
                if(e.rn_r2_reg==execInstr.rob_dest){
                    e.rn_r2_rdy =1;
                }
            }
            for(Instruction e: RN){
                if(e.rn_r1_reg==execInstr.rob_dest){
                    e.rn_r1_rdy =1;
                }
                if(e.rn_r2_reg==execInstr.rob_dest){
                    e.rn_r2_rdy =1;
                }
            }
            for(Instruction e: IQ){
                if(e.rn_r1_reg==execInstr.rob_dest){
                    e.rn_r1_rdy =1;
                }
                if(e.rn_r2_reg==execInstr.rob_dest){
                    e.rn_r2_rdy =1;
                }
            }
        }
    }
    
    private void set_rob_ready(Instruction completedInstruction){
        System.out.println(completedInstruction.instr_number +" REDY BIT FOR ROB_LOC "+completedInstruction.rob_dest);
        mROB[completedInstruction.rob_dest].ready_bit=1;
    }
    private int remove_rob(int instrNum){
        reorder_buffer_entry headOfROB = mROB[mROB_Head];
        System.out.println(mSimulatorTime + " ROB: "+ headOfROB.instr_number +" ready_bit{"+headOfROB.ready_bit+"} dst"+headOfROB.dst+"}");
        if(headOfROB.ready_bit == 1 && instrNum == headOfROB.instr_number){ //instr is ready to retire
            //Check rmt if same instr make it invalid
            rename_table_entry rmt_enter_validation = rename_map_table.get(mROB_Head);
            if(rmt_enter_validation!=null){
                if(rmt_enter_validation.instr_number == headOfROB.instr_number){
                    rmt_enter_validation.valid_bit = 0;
                }
            }
            mROB_Head++;
            return 1;
        }
        return -1;
    }
    public void allocateROB(Instruction addToROB){
        reorder_buffer_entry rob_entry = new reorder_buffer_entry();
        rob_entry.ready_bit = 0;
        rob_entry.instr_number = addToROB.instr_number;
        rob_entry.valid = 1;
        if(addToROB.dst_reg != -1){
            //Add to the queue
            rob_entry.dst = addToROB.dst_reg;  //modify the dst for the instr
            addToROB.rob_dest=mROB_Tail;       //instrs knows where in the ROB to look
            mROB[mROB_Tail]=rob_entry;         //To the tail add the instr
            mElementsInROB++;                  //inc number of elements in the ROB
            //
            rename_instruction_src(addToROB);  //rename the instr src based on rmt
            //Add to RMT
            rename_table_entry rmt_enter = new rename_table_entry();
            rmt_enter.valid_bit = 1;           //create a rmt entry for the dest reg 
            rmt_enter.rob_tag   = mROB_Tail;   // 
            rmt_enter.instr_number = rob_entry.instr_number; 
            rename_map_table.put(Integer.valueOf(rob_entry.dst),rmt_enter);
            rename_instruction_dst(addToROB);
        }
        else{
            mElementsInROB++;
            rob_entry.dst = mROB_Tail;
            rename_instruction_src(addToROB);
            mROB[mROB_Tail]=(rob_entry);
            rename_instruction_dst(addToROB);
        }
        if(mROB_Tail==mROB_Size){
            mROB_Tail=0;
        }
        else{
            mROB_Tail++;
        }
    }
    
    public void rename_instruction_src(Instruction renameInstr){
        rename_table_entry src_renaming;
        //R1 renaming
        if(renameInstr.src_r1_reg != -1){
            src_renaming = rename_map_table.get(Integer.valueOf(renameInstr.src_r1_reg));
            if(src_renaming != null){
                if(src_renaming.valid_bit == 1){
                    renameInstr.rn_r1_reg = src_renaming.rob_tag;
                    renameInstr.rn_r1_rdy = 0;
                }
                else{
                    renameInstr.rn_r1_reg = -55;                                  //Since the RMT didn't have it therefore ARF has most recent value, 55 denotes that
                    renameInstr.rn_r1_rdy = 0;
                }
            }
            else{
                renameInstr.rn_r1_reg = -55;                                         //No register, immediate value
                renameInstr.rn_r1_rdy = 1;
            }
        }
        else{
            renameInstr.rn_r1_reg = -1;                                         //No register, immediate value
            renameInstr.rn_r1_rdy = 1;
        }
        //R2 renaming
        if(renameInstr.src_r2_reg != -1){
            src_renaming = rename_map_table.get(Integer.valueOf(renameInstr.src_r2_reg));
            if(src_renaming != null){
                if(src_renaming.valid_bit == 1){
                    renameInstr.rn_r2_reg = src_renaming.rob_tag;
                    renameInstr.rn_r2_rdy = 0;
                }
                else{
                    renameInstr.rn_r2_reg = -55;                                  //Since the RMT didn't have it therefore ARF has most recent value, 55 denotes that
                    renameInstr.rn_r2_rdy = 0;
                }
            }
            else{
                renameInstr.rn_r2_reg = -55;                                  //Since the RMT didn't have it therefore ARF has most recent value, 55 denotes that
                renameInstr.rn_r2_rdy = 0;
            }
        }
        else{
            renameInstr.rn_r2_reg = -1;                                         //No register, immediate value
            renameInstr.rn_r2_rdy = 1;
        }

    }
    
    public void rename_instruction_dst(Instruction renameInstr){
        //DST renanme
        rename_table_entry src_renaming;
        if(renameInstr.dst_reg != -1){
            src_renaming = rename_map_table.get(Integer.valueOf(renameInstr.dst_reg));
            if(src_renaming != null){
                if(src_renaming.valid_bit == 1){
                    renameInstr.rn_dst_reg = src_renaming.rob_tag;
                }
                else{
                    renameInstr.rn_dst_reg = src_renaming.rob_tag;
                }
            }
        }
        else{
            renameInstr.rn_dst_reg = -1;
        }
    }
    ///////////////////////////////////////////////////////////////////////////
    public void advanceTime(){
        mSimulatorTime++;
    }
    public boolean advanceCycle(){
        return (trace_file_empty && mPipelineEmpty);
    }
    public void checkIfPipelineEmpty(){
        if(DE.size() ==0 && RN.size()==0 && RR.size()==0 && DI.size() ==0 && IQ.size()==0 && execute_list.size()==0 && WB.size()==0){
            //System.out.println("Setting true pl DE");
            mPipelineEmpty=true;
        }
        else{
            mPipelineEmpty=false;
            //System.out.println("Setting false pl DE "+ " RN "+RN.size());
        }
    }
    //Display methods
    public void displayConfiguration(){
        System.out.printf("# === Processor Configuration ===\n");
        System.out.printf("# ROB_SIZE = %d\n", mROB_Size);
        System.out.printf("# IQ_SIZE  = %d\n", mIQ_Size);
        System.out.printf("# WIDTH    = %d\n", mWidth);
    }
    public void displaySimulationResults(){
        System.out.printf("# === Simulation Results ========\n");
        System.out.printf("# Dynamic Instruction Count = %d\n",mDynamicInstrCount);
        System.out.printf("# Cycles  = %d\n", mSimulatorTime-1);
        System.out.printf("# Instructions Per Cycle (IPC) =%d\n", (mSimulatorTime-1)/mDynamicInstrCount);
    }
    public void printContents(){
//        for(Instruction e: DE){
//            System.out.println("DE: "+ e.instr_number);
//        }
//        for(Instruction e: RR){
//            System.out.println("RR: "+ e.instr_number);
//        }
//        for(Instruction e: RN){
//            System.out.println("RN: "+ e.instr_number);
//        }
       for(Integer key:rename_map_table.keySet()){
           rename_table_entry temp = rename_map_table.get(key);
           if(temp!= null) System.out.println("Key: " + key+ " Valid: "+temp.valid_bit + " ROB TAG "+ temp.rob_tag+ " INST NUM "+ temp.instr_number);
       }
       for(int i=0; i<mROB_Size; i++){
           reorder_buffer_entry temp = mROB[i];
           if(temp!= null) System.out.println("ROB_LOC: " + i + " dst: "+temp.dst + " ready "+ temp.ready_bit+ " INST NUM "+ temp.instr_number);
       }
    }
    
}
