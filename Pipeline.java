/**
 *
 * @author Priyank Kashyap
 * NCSU
 */
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.io.*;
import java.util.Collections;
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
        DE = new ArrayList<Instruction>();
        RN = new ArrayList<Instruction>();
        RR = new ArrayList<Instruction>();
        DI = new ArrayList<Instruction>();
        IQ = new ArrayList<Instruction>();
        WB = new ArrayList<Instruction>();
        execute_list = new ArrayList<Instruction>();
        //ROB initialization
        reorder_buffer_entry empty_rob = new reorder_buffer_entry();
        empty_rob.dst =-55;
        empty_rob.valid=0;
        empty_rob.ready_bit=0;
        empty_rob.instr_number=-1;
        mROB = new reorder_buffer_entry[mROB_Size];
        for(int i =0; i< mROB_Size ; i++){
            mROB[i]=empty_rob;
        }
        mROB_Head =-1;
        mROB_Tail=-1;
        //RMT Initialization
        rename_map_table = new HashMap();
        for(int i = 0;i<numberOfARFRegs; i++){
            rename_map_table.put(Integer.valueOf(i),null);
        }
    }
    //-------------Pipeline Functions-------------------------------------//
    public void fetch() throws IOException{
        if(DE.size()==0){
            for (int i=0; i<mWidth; i++){ 
                String line=br.readLine();           
                Instruction temp = new Instruction();
                int time_in_fetch=1;
                int op_type ,dest, src1, src2;                       // Variable read from input file
                if(line != null){
                    String [] split   = line.split("\\s+");               // split line at whitespace
                    long pc           = Long.parseLong(split[0], 16);     // gets address from String split and converts to long. parseLong uses base 16
                    temp.instr_number = mDynamicInstrCount;
                    temp.op_type      = Integer.parseInt(split[1], 10);
                    temp.dst_reg      = Integer.parseInt(split[2], 10);
                    temp.src_r1_reg   = Integer.parseInt(split[3], 10);
                    temp.src_r2_reg   = Integer.parseInt(split[4], 10);
                    temp.fe_time      = mSimulatorTime;
                    temp.de_time      = mSimulatorTime+1;
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
        if(DE.size()>0){
            if(RN.size() == 0){
                int removal = mWidth;
                if(DE.size() < mWidth) 
                    removal=DE.size();
                for(int i= 0; i < removal; i++){                    
                    Instruction temp= DE.get(i);
                    temp.rn_time = mSimulatorTime+1;
                    RN.add(temp);
                }

                for(int i=removal-1; i >= 0; i--){
                    DE.remove(i);
                }
            }
        }
    }
    
    public void rename(){
        if(RN.size()>0){ //there is a RN bundle
            if((RR.size()== 0) && (mElementsInROB+RN.size())< mROB_Size){ //if RR is empty and there is space in the ROB to accept the RN bundle
                int removal = mWidth;
                if(RN.size() < mWidth) 
                    removal=RN.size();
                for(int i= 0; i < removal; i++){                    
                    Instruction temp= RN.get(i);
                    allocateROB_arr(temp);
                    temp.rr_time = mSimulatorTime+1;                    
                    RR.add(temp);
                }

                for(int i=removal-1; i >= 0; i--){
                    RN.remove(i);
                }
            }
        }
    }

    public void register_read(){
        if(RR.size()>0){
            if(DI.size() == 0){
                int removal = mWidth;
                if(RR.size() < mWidth) 
                    removal=RR.size();
                for(int i= 0; i < removal; i++){                    
                    Instruction temp= RR.get(i);
                    temp.di_time = mSimulatorTime+1;
                    instructionReady(temp);
//                    System.out.print("RR: ");
//                    temp.display_internal();
                    DI.add(temp);
                }

                for(int i=removal-1; i >= 0; i--){
                    RR.remove(i);
                }
            }
        }
    }   
    
    public void dispatch(){
        if(DI.size()>0){
            if(DI.size() <= (mIQ_Size- IQ.size())){
                int removal = mWidth;
                if(DI.size() < mWidth)
                    removal=DI.size();
                
                for(int i= 0; i < removal; i++){
                    Instruction temp= DI.get(i);
                    temp.is_time= mSimulatorTime+1;
//                    System.out.print("DI: ");
//                    temp.display_internal();
                    IQ.add(temp);
                }
                
                for(int i=removal-1; i >= 0; i--){
                    DI.remove(i);
                }
            }
        }
    }
        
    public void issue(){
        int possibleExec = mWidth;
        int currentExec = 0;
        if(IQ.size()>0){ 
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
            int removal = mIQ_Size;
            if(IQ.size() < mIQ_Size) removal =IQ.size();      
            for(int i = 0; i <removal; i++){
                Instruction temp= IQ.get(i);
                if(temp.rn_r1_rdy==1 && temp.rn_r2_rdy==1){
                    if(currentExec ==possibleExec){
                        break;
                    }
                    //temp.ex_time =mSimulatorTime+1;
                    IQ.set(i, null);
                    execute_list.add(temp);
                    currentExec++;
                }
            }
            IQ.removeAll(Collections.singleton(null));
        }
    }
    
    public void execute(){
        if(execute_list.size()>0){
            for(Instruction i:execute_list){
                if(i != null){
                    if(i.ex_time == 0){
                        i.ex_time = mSimulatorTime;
                    }
                    if(i.op_type==0){
                        i.stop_execute_time=i.ex_time;
                    }
                    else if(i.op_type==1){
                        i.stop_execute_time=i.ex_time+1;
                    }
                    else if(i.op_type==2){
                        i.stop_execute_time=i.ex_time+4;
                    }
                }
            }
            int removal = mWidth*5;
            if(execute_list.size() < (mWidth*5)) removal =execute_list.size(); 
            for(int i = 0; i <removal; i++){
                Instruction temp= execute_list.get(i);
                if(temp.stop_execute_time == mSimulatorTime){
                    execute_list.set(i, null);
                    wakeup(temp);
                    temp.wb_time= mSimulatorTime+1;
//                    System.out.print("EX: ");
//                    temp.display_internal();
                    WB.add(temp);
                }               
            }
            execute_list.removeAll(Collections.singleton(null));
        }
    }    
    
    public void writeback(){
        if(WB.size()>0){
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
                Instruction set_instr_rdy = WB.get(i);
                if(set_instr_rdy!= null) set_rob_ready(set_instr_rdy);
            }
        }
    }   
        
    public void retire(){
        int currentExec = 0;
        if(WB.size()>0){
            for(Instruction i:WB){
                if(i != null){
                    if(i.rt_time == 0){
                        i.rt_time = i.wb_time+1;
                    }
                }
            }
            //loop to move to next stage
            int removal = mWidth;
            if(WB.size() < mWidth) 
                removal=WB.size();
            for(int i= 0; i < removal; i++){
                Instruction toRetire = WB.get(i);                
                int status = remove_rob(toRetire);
                if(status < 0) break;
                else {
                    if(currentExec == mWidth){
                        return;
                    }
                    WB.set(i,null);
                    if(toRetire != null) {
                        toRetire.finish_time=mSimulatorTime;
                        toRetire.display_time();
//                        System.out.println("----------------------------------------------------------------");
//                        System.out.println("ROB_Head: "+mROB_Head +" ROB_Tail: "+mROB_Tail + " Elements in ROB: "+ mElementsInROB);
//                        for(int j =0; j < mROB_Size; j++){
//                            reorder_buffer_entry e = mROB[j];
//                            System.out.println("enque rob_dst: "+j+" rob_arf_addr "+e.dst+" rob_instr " + e.instr_number + " valid bit " + e.valid + " ready_bit " + e.ready_bit);
//                        }
//                        for(Integer key:rename_map_table.keySet()){
//                            rename_table_entry temp = rename_map_table.get(key);
//                            if(temp!= null) System.out.println("Key: " + key+ " Valid: "+temp.valid_bit + " ROB TAG "+ temp.rob_tag+ " INST NUM "+ temp.instr_number);
//                        }
//                        for(Instruction e:IQ){
//                            System.out.println("Instr_Number: " + e.instr_number+ " Ready {"+e.rn_r1_rdy+","+e.rn_r2_rdy + "} ROB TAG "+ e.rob_dest + " rn_reg {"+e.rn_r1_reg+","+e.rn_r2_reg + "}");
//                        }
                    }
                    currentExec++;
                }
            }
            WB.removeAll(Collections.singleton(null));
        }
    }    
    //-------------Control Methods--------------------------------------//
    public void advanceTime(){
        mSimulatorTime++;
    }
        
    public boolean advanceCycle(){
        return (trace_file_empty && mPipelineEmpty);
    }
    
    public void checkIfPipelineEmpty(){
        if(DE.size() ==0 && RN.size()==0 && RR.size()==0 && DI.size() ==0 && IQ.size()==0 && execute_list.size()==0 && WB.size()==0){            
            mPipelineEmpty=true;
        }
        else{
            mPipelineEmpty=false;
        }
    }
    //------------------Display Methods--------------------------//
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
    //-----------------ROB Methods-------------------------------//
    
    public void allocateROB_arr(Instruction addToROB){
        reorder_buffer_entry rob_entry = new reorder_buffer_entry();
        rob_entry.ready_bit = 0;
        rob_entry.instr_number = addToROB.instr_number;
        rob_entry.valid = 1;
        if(addToROB.dst_reg != -1){
            //Add to the queue
            rob_entry.dst = addToROB.dst_reg;  //modify the dst for the instr
            enQueue(rob_entry);                 //To the tail add the instr
            mElementsInROB++;
            addToROB.rob_dest=mROB_Tail;       //instrs knows where in the ROB to look
            rename_instruction_src(addToROB);  //rename the instr src based on rmt            
            rename_table_entry rmt_enter = new rename_table_entry(); //Add to RMT
            rmt_enter.valid_bit = 1;           //create a rmt entry for the dest reg 
            rmt_enter.rob_tag   = mROB_Tail;   
            rmt_enter.instr_number = rob_entry.instr_number; 
            rename_map_table.put(Integer.valueOf(rob_entry.dst),rmt_enter);
            rename_instruction_dst(addToROB);
        }
        else{
            rob_entry.dst = -1;
            enQueue(rob_entry);                 //To the tail add the instr
            mElementsInROB++;
            addToROB.rob_dest=mROB_Tail;        //instrs knows where in the ROB to look
            mROB[mROB_Tail]=(rob_entry);
            rename_instruction_src(addToROB);
        }
    }
    
    private void enQueue(reorder_buffer_entry entry){         
        //ROB is full
        if ((mROB_Head == 0 && mROB_Tail == mROB_Size-1) || (mROB_Tail == (mROB_Head-1)%(mROB_Size-1))){ 
            return; 
        } 

        else if (mROB_Head == -1) /* Insert First Element */{ 
            mROB_Head = 0; 
            mROB_Tail = 0; 
            mROB[mROB_Tail] = entry; 
        } 
        
        //going circular
        else if (mROB_Tail == mROB_Size-1 && mROB_Head != 0){ 
            mROB_Tail = 0; 
            mROB[mROB_Tail] = entry; 
        } 

        else { 
            mROB_Tail++; 
            mROB[mROB_Tail] = entry; 
        } 
    }
    
    
    public void rename_instruction_src(Instruction renameInstr){
        rename_table_entry src_renaming;
        //R1 renaming
        if(renameInstr.src_r1_reg != -1){
            src_renaming = rename_map_table.get(Integer.valueOf(renameInstr.src_r1_reg));
            if(src_renaming != null){
                if(src_renaming.valid_bit == 1){                                  //Valid entry
                    renameInstr.rn_r1_reg = src_renaming.rob_tag;
                    renameInstr.rn_r1_rdy = mROB[src_renaming.rob_tag].ready_bit;
                }
                else{
                    renameInstr.rn_r1_reg = -55;                                  //Since the RMT didn't have it therefore ARF has most recent value, 55 denotes that
                }
            }
            else{
                renameInstr.rn_r1_reg = -55;                                         //No entry in the RMT, value from ARF
            }
        }
        else{
            renameInstr.rn_r1_reg = -1;                                         //No register, immediate value
        }
        //R2 renaming
        if(renameInstr.src_r2_reg != -1){
            src_renaming = rename_map_table.get(Integer.valueOf(renameInstr.src_r2_reg));
            if(src_renaming != null){
                if(src_renaming.valid_bit == 1){
                    renameInstr.rn_r2_reg = src_renaming.rob_tag;
                    renameInstr.rn_r2_rdy = mROB[src_renaming.rob_tag].ready_bit;
                }
                else{
                    renameInstr.rn_r2_reg = -55;                                  //Since the RMT didn't have it therefore ARF has most recent value, 55 denotes that
                }
            }
            else{
                renameInstr.rn_r2_reg = -55;                                      //Since the RMT didn't have it therefore ARF has most recent value, 55 denotes that
            }
        }
        else{
            renameInstr.rn_r2_reg = -1;                                         //No register, immediate value
        }
        //System.out.print("#"+mSimulatorTime+" RENAME Instr: "+renameInstr.instr_number+ " src_rename_1 :"+ renameInstr.rn_r1_reg + " src_rename_2 "+renameInstr.rn_r2_reg+ " " );
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
    public void instructionReady(Instruction readyInstr){
        if( readyInstr.rn_r1_reg ==-1 || readyInstr.rn_r1_reg ==-55){      //both immediate values or both arf values
            readyInstr.rn_r1_rdy = 1;
        }
        else{
            if(readyInstr.rn_r1_rdy ==0 ){
                readyInstr.rn_r1_rdy = mROB[readyInstr.rn_r1_reg].ready_bit;
            }
        }
        if( readyInstr.rn_r2_reg ==-1 || readyInstr.rn_r2_reg ==-55){      //both immediate values or both arf values
            readyInstr.rn_r2_rdy = 1;
        }
        else{
            if(readyInstr.rn_r2_rdy ==0 ){
                readyInstr.rn_r2_rdy = mROB[readyInstr.rn_r2_reg].ready_bit;
            }
        }
    }

    public void wakeup(Instruction execInstr){
        if(execInstr.dst_reg != -1){
            for(Instruction e: RR){
                if(e.rn_r1_reg==execInstr.rob_dest){
                        e.rn_r1_rdy =1;
//                        System.out.println("WAKEUP RR R1  exec:"+ execInstr.instr_number+ " to be woken up:"+ e.instr_number);
                    }
                if(e.rn_r2_reg==execInstr.rob_dest){
                    e.rn_r2_rdy =1;
//                    System.out.println("WAKEUP RR R2  exec:"+ execInstr.instr_number+ " to be woken up:"+ e.instr_number);
                }
            }
            for(Instruction e: DI){
                if(e.rn_r1_reg == execInstr.rob_dest){
                    e.rn_r1_rdy =1;
//                    System.out.println("WAKEUP DI R1  exec:"+ execInstr.instr_number+ " to be woken up:"+ e.instr_number);
                }
                if(e.rn_r2_reg == execInstr.rob_dest){
                    e.rn_r2_rdy =1;
//                    System.out.println("WAKEUP DI R2  exec:"+ execInstr.instr_number+ " to be woken up:"+ e.instr_number);
                }
            }

            for(Instruction e: IQ){
                if(e!=null){
                    if(e.rn_r1_reg==execInstr.rob_dest){
                        e.rn_r1_rdy =1;
//                        System.out.println("WAKEUP IQ R1  exec:"+ execInstr.instr_number+ " to be woken up:"+ e.instr_number);
                    }
                    if(e.rn_r2_reg==execInstr.rob_dest){
                        e.rn_r2_rdy =1;
//                        System.out.println("WAKEUP IQ R2  exec:"+ execInstr.instr_number+ " to be woken up:"+ e.instr_number);
                    }
                }
            }
        }
    }
    
    private void set_rob_ready(Instruction completedInstruction){
        mROB[completedInstruction.rob_dest].ready_bit = 1;
        mROB[completedInstruction.rob_dest].rt_time = mSimulatorTime+1;
    }
    
    
    private void deQueue(){ 
        //empty queue
        if (mROB_Head == -1) {              
        } 
        //invalidate the rob entry
        mElementsInROB--;
        reorder_buffer_entry data = mROB[mROB_Head]; 
        data.valid=0;
        data.ready_bit=0;
        
        if (mROB_Head == mROB_Tail){ //empty
            mROB_Head = -1; 
            mROB_Tail = -1; 
        } 
        else if (mROB_Head == mROB_Size-1) //loop
            mROB_Head = 0; 
        else
            mROB_Head++; 

    } 
    
    private int remove_rob(Instruction toRetire){
        reorder_buffer_entry headOfROB = mROB[mROB_Head];
//        System.out.println("remove instr_num"+toRetire.instr_number);
        if(headOfROB.ready_bit == 1 && toRetire.instr_number == headOfROB.instr_number){ //instr is ready to retire
            toRetire.finish_time =mSimulatorTime;
            if(toRetire.dst_reg != -1){
                rename_table_entry rmt_enter_validation = rename_map_table.get(headOfROB.dst);
                if(rmt_enter_validation.instr_number == headOfROB.instr_number){
                    rmt_enter_validation.valid_bit = 0;
                }
            }
//            System.out.println("removing instr from rob instr_num "+toRetire.instr_number);
            deQueue();
            
            return 1;       
        }
        return -1;
    }
    
}
