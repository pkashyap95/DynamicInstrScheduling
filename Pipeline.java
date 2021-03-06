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

public class Pipeline {
    private final int numberOfARFRegs=67;
    private final int mWidth;
    private final int mROB_Size;
    private final int mIQ_Size;
    private double mSimulatorTime;
    private double mDynamicInstrCount;
    private int mElementsInROB;
    private boolean trace_file_empty;
    private BufferedReader br;
    private boolean mPipelineEmpty;
    private Map<Integer,rename_table_entry> rename_map_table;
    private reorder_buffer_entry [] mROB;
    private int mROB_Head, mROB_Tail;
    private ArrayList <Instruction> DE,RN,RR,DI, IQ, execute_list, WB, RT;
    
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
        RT = new ArrayList<Instruction>();
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
                    temp.instr_number = (int)mDynamicInstrCount;
                    temp.op_type      = Integer.parseInt(split[1], 10);
                    temp.dst_reg      = Integer.parseInt(split[2], 10);
                    temp.src_r1_reg   = Integer.parseInt(split[3], 10);
                    temp.src_r2_reg   = Integer.parseInt(split[4], 10);
                    temp.fe_time      = (int)mSimulatorTime;                    
                    DE.add(temp);
                    mDynamicInstrCount++;
                }
                else{
                    trace_file_empty = true;
                }
            }
        }        
    }

    public void decode(){  
        if(DE.size()>0){
            for(Instruction e: DE){
                if(e != null){
                    if(e.de_time == 0){
                        e.de_time = (int) mSimulatorTime;
                    }
                }
            }
            if(RN.size() == 0){
                int mRemove = mWidth;
                if(DE.size() < mWidth) mRemove = DE.size();
                for(int i=0; i < mRemove ; i++){
                    Instruction temp = DE.get(i);
                    RN.add(temp);
                }
                for(int i=mRemove -1; i >=0; i--){
                    DE.remove(i);
                }
            }
            else{
                return;
            }
        }
    }
    
    public void rename(){
        if(RN.size() > 0){
            for(Instruction e: RN){
                if(e != null){
                    if(e.rn_time == 0){
                        e.rn_time = (int) mSimulatorTime;
                    }
                }
            }
            if((RR.size()== 0) && (mElementsInROB + RN.size() <= mROB_Size)){
                int mRemove = mWidth;
                if(RN.size() < mWidth) mRemove = RN.size();
                
                for(int i =0; i < mRemove ; i++){                    
                    Instruction temp = RN.get(i);                    
                    allocateROB_arr(temp);
                    RR.add(temp);
                }
                for(int i = mRemove-1; i >= 0; i--){
                    RN.remove(i);
                }
            }
        }
        else{
            return;
        }
    }

    public void register_read(){
        if(RR.size() > 0){
            for(Instruction e: RR){
                if(e != null){
                    if(e.rr_time == 0){
                        e.rr_time = (int) mSimulatorTime;
                    }
                }
            }
            if(DI.size() == 0){
                int mRemove = mWidth;
                if(RR.size() < mWidth) mRemove = RR.size();
                for(int i =0; i < mRemove ; i++){
                    Instruction temp = RR.get(i);
                    instructionReady(temp);
                    DI.add(temp);
                }               
                for(int i = mRemove-1; i >=0; i--){
                    RR.remove(i);
                }
            }
        }
        else{
            return;
        }
    }   
    
    public void dispatch(){
        if(DI.size()>0){
            for(Instruction e: DI){
                if(e != null){
                    if(e.di_time == 0){
                        e.di_time = (int) mSimulatorTime;
                    }
                }
            }            
            if(DI.size() <= (mIQ_Size- IQ.size())){
                
                int removal = mWidth;
                if(DI.size() < mWidth) removal=DI.size();                
                for(int i= 0; i < removal; i++){
                    Instruction temp= DI.get(i);
                    IQ.add(temp);
                }

                for(int i=removal-1; i >= 0; i--){
                    DI.remove(i);
                }
            }
        }
        else{
            return;
        }
    }
        
    public void issue(){

        int possibleExec = mWidth;
        int currentExec = 0;
        if(IQ.size()>0){
            for(Instruction e: IQ){
                if(e != null){
                    if(e.is_time == 0){
                        e.is_time = (int) mSimulatorTime;
                    }
                }
            }

            for(int i = 0; i <IQ.size()-1; i++){
                int oldest_instr = i;
                for(int j=i+1; j< IQ.size();j++){
                    if(IQ.get(j).instr_number < IQ.get(oldest_instr).instr_number){
                        oldest_instr = j;
                    }
                }               
                Instruction temp_1 = IQ.get(i); 
                Instruction temp_2 = IQ.get(oldest_instr); 
                IQ.set(oldest_instr, temp_1); 
                IQ.set(i, temp_2);
            }
            int removal = mIQ_Size;
            if(IQ.size() < mIQ_Size) removal =IQ.size();    
            
            for(int i = 0; i <removal; i++){
                Instruction temp= IQ.get(i);
                if(temp.rn_r1_rdy==1 && temp.rn_r2_rdy==1){
                    if(currentExec == possibleExec){
                        break;
                    }
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
                        i.ex_time = (int) mSimulatorTime;
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
            }
            int removal = mWidth*5;
            if(execute_list.size() < (mWidth*5)) removal = execute_list.size(); 
            for(int i = 0; i <removal; i++){
                Instruction temp= execute_list.get(i);
                if(temp.stop_execute_time == mSimulatorTime){
                    execute_list.set(i, null);
                    wakeup(temp);
                    WB.add(temp);
                }               
            }
            execute_list.removeAll(Collections.singleton(null));
        }
    }    
    
    public void writeback(){
        if(WB.size()> 0){          
            for(Instruction e: WB){                
                if(e.wb_time == 0){
                    e.wb_time = (int) mSimulatorTime;
                }
            }
            int removal = mWidth*5;
            if(WB.size() < removal) removal = WB.size();
            for(int i=0; i < removal; i++){                
                Instruction temp = WB.get(i);
                set_rob_ready(temp);
                RT.add(temp);
            }
            for(int j = removal -1; j >=0; j--){
                WB.remove(j);
            }
        }
    }
    
    public void retire(){
        int currentExec = 0;
        if(RT.size() > 0){
            for(Instruction e : RT){
                if(e != null){
                    if(e.rt_time == 0){
                        e.rt_time = (int) mSimulatorTime;
                    }
                }
            }
            for(int i = 0; i <RT.size()-1; i++){
                int min = i;
                for(int j=i+1; j<RT.size();j++){
                    if(RT.get(j).instr_number < RT.get(min).instr_number){
                        min = j;
//                        System.out.println("i :"+ i + " oldest_instr: " + oldest_instr);
                    }
                }
                Instruction temp_1 = RT.get(i); 
                Instruction temp_2 = RT.get(min);
                RT.set(min, temp_1); 
                RT.set(i, temp_2); 
            }
            //loop to move to next stage
            int removal = mWidth;
            if(RT.size() < mWidth) 
                removal=RT.size();
            
            for(int i=0; i < removal; i++){
                Instruction toRetire = RT.get(i);        
                if(toRetire != null){
                    int status = remove_rob(toRetire);
//                    System.out.println("Instr: "+ toRetire.instr_number + " remove_rob_status: "+ status);
                    if(status < 0){
                        RT.removeAll(Collections.singleton(null));
                        
                        return;
                    }
                    else {
                        if(currentExec == mWidth){
                            return;
                        }
                        RT.set(i,null);
                        if(toRetire != null) {
                            if(mSimulatorTime == toRetire.rt_time) toRetire.finish_time = 1;
                            else toRetire.finish_time = (int) (mSimulatorTime - toRetire.rt_time)+1;
                            toRetire.display_time();
                        }
                        currentExec++;
                    }
                }
            }
            RT.removeAll(Collections.singleton(null));
        }
    }    
    //-------------Control Methods--------------------------------------//
    public void advanceTime(){
        mSimulatorTime++;
    }
        
    public boolean advanceCycle(){
        return (mPipelineEmpty && trace_file_empty);
    }
    
    public void checkIfPipelineEmpty(){
        if(DE.size() ==0 && RN.size()==0 && RR.size()==0 && DI.size() ==0 && IQ.size()==0 && execute_list.size()==0 && WB.size()==0 && RT.size() == 0){            
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
        double ipc = (mDynamicInstrCount/mSimulatorTime);
        System.out.printf("# === Simulation Results ========\n");
        System.out.printf("# Dynamic Instruction Count = %d\n", (int)mDynamicInstrCount);
        System.out.printf("# Cycles  = %d\n", (int)mSimulatorTime);
        System.out.printf("# Instructions Per Cycle (IPC) = %s\n", String.format("%.02f",ipc));
        //System.out.println("# Instructions Per Cycle (IPC) = "+ ipc);
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

        else if (mROB_Head == -1) {         //insert First Element 
            mElementsInROB = 1;
            mROB_Head = 0; 
            mROB_Tail = 0; 
            mROB[mROB_Tail] = entry; 
        } 
        
        //going circular
        else if (mROB_Tail == mROB_Size-1 && mROB_Head != 0){ 
            mROB_Tail = 0; 
            mROB[mROB_Tail] = entry; 
            mElementsInROB++;
        } 

        else { 
            mROB_Tail++; 
            mROB[mROB_Tail] = entry; 
            mElementsInROB++;
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
                    }
                if(e.rn_r2_reg==execInstr.rob_dest){
                    e.rn_r2_rdy =1;
                }
            }
            for(Instruction e: DI){
                if(e.rn_r1_reg == execInstr.rob_dest){
                    e.rn_r1_rdy =1;
                }
                if(e.rn_r2_reg == execInstr.rob_dest){
                    e.rn_r2_rdy =1;
                }
            }

            for(Instruction e: IQ){
                if(e!=null){
                    if(e.rn_r1_reg==execInstr.rob_dest){
                        e.rn_r1_rdy =1;
                    }
                    if(e.rn_r2_reg==execInstr.rob_dest){
                        e.rn_r2_rdy =1;
                    }
                }
            }
        }
    }
    
    private void set_rob_ready(Instruction completedInstruction){
        mROB[completedInstruction.rob_dest].ready_bit = 1;
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
        if(headOfROB.ready_bit == 1 && toRetire.instr_number == headOfROB.instr_number){ //instr is ready to retire            
            if(toRetire.dst_reg != -1){
                rename_table_entry rmt_enter_validation = rename_map_table.get(headOfROB.dst);
                if(rmt_enter_validation.instr_number == headOfROB.instr_number){
                    rmt_enter_validation.valid_bit = 0;
                }
            }
            for(Instruction e: RR){
                if(e.rn_r1_reg==toRetire.rob_dest){
                        e.rn_r1_rdy =1;
                    }
                if(e.rn_r2_reg==toRetire.rob_dest){
                    e.rn_r2_rdy =1;
                }
            }
            deQueue();
            return 1;       
        }
        return -1;
    }
}
