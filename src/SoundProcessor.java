import java.io.*;
import javax.sound.sampled.*;
import java.util.*;

public class SoundProcessor{
    private ArrayList<AudioInputStream> files;
    private int bpm;
    private ArrayList<byte[]> signatures;

    public SoundProcessor(ArrayList<AudioInputStream> files, int bpm){
        this.files = files;
        this.bpm = bpm;
        signatures = new ArrayList<byte[]>();
    }
    
    public ArrayList<NoteList> process(){ // NoteList for 8 lanes, any number of NoteList arrays for any number of measures
            ArrayList<AudioFormat> af = new ArrayList<AudioFormat>();
            ArrayList<NoteList> noteLists = new ArrayList<NoteList>();
			double[] byteArraySize = new double[files.size()];
			byte[] chunk;
			int[] newChunkFirstHalf = {0,0,0,0,0};
			int[] prevChunkSecondHalf = {0,0,0,0,0};
			double sixteenthLength;
			boolean foundNotes = false;
			
			for(int i = 0; i < files.size(); i++){
				af.add((files.get(i)).getFormat());
				System.out.println(af.get(i).toString());
			}
			
			// Half note               =  120 / BPM
			// Quarter note            =   60 / BPM
			// Eighth note             =   30 / BPM
			// Sixteenth note          =   15 / BPM
			
			sixteenthLength = 15.0/bpm;
			System.out.println("Length of 16th note is " + sixteenthLength);
			
			// formulae for bytes to read:
			// bytes = seconds * sample rate * channels * (bits per sample / 8)
			// OR 
			// bytes = seconds * sample rate * frame size
			
			for(int i = 0; i < files.size(); i++){
			byteArraySize[i] = sixteenthLength * af.get(i).getSampleRate() * af.get(i).getFrameSize();
			System.out.println("Byte array size for file " + i + " is " + (int)byteArraySize[i]);
			}
			
			boolean checkEOF = false;
			int[] n = new int[files.size()];
			
			for(int measure = 0; !checkEOF; measure++){ // !checkEOF
                NoteList tempList = new NoteList();
				for(int lane = 0; lane < files.size(); lane++){
                    System.out.println("Measure " + measure + " in file " + lane);
					if (n[lane] != -1){
						for(int tokens = 0; tokens < 16; tokens++){
								chunk = new byte[(int)byteArraySize[lane]];
								//System.out.println("chunk size " + (int)byteArraySize[lane]);
                                try{
                                n[lane] = files.get(lane).read(chunk, 0, chunk.length);
                                } catch (Exception ex) {
                                    ex.printStackTrace();
                                    System.exit(1);
                                }
								if(!foundNotes){ // if it's the very first note
									if(getTotalAmp(chunk) >= 100000){
										foundNotes = true;
                                        addSignature(chunk);
                                        tempList.addNote(new Note(tokens, lane, signatures.size()));
                                        System.out.println("Note found! M" + measure + ":L" + lane + ":P" + tokens);
                                    }
								}
								
								else{
									newChunkFirstHalf[lane] = getTotalAmp(Arrays.copyOfRange(chunk, 0, chunk.length/2 -1));
                                    //if (lane == 4 && measure >= 12 && measure <= 13) System.out.println("measure: " + measure + " lane: " + lane + " 16th interval: " + tokens + " ncfh: " + newChunkFirstHalf[lane] + " pcsh: " + prevChunkSecondHalf[lane] + " th: " + (int)(newChunkFirstHalf[lane] * (0.92)));
									// TODO: fix > threshold
                                    if (((newChunkFirstHalf[lane] * (0.92)) - prevChunkSecondHalf[lane]) > -(newChunkFirstHalf[lane] - prevChunkSecondHalf[lane])*0.15 && newChunkFirstHalf[lane] > 60000 ){
                                    	System.out.println("Note found! M" + measure + ":L" + lane + ":P" + tokens);
                                    	int sigcheck = compareSignatures(chunk);
                                        
                                    	if (sigcheck < 0){
                                    		System.out.println("Adding new signature");
                                    		addSignature(chunk);
                                    		tempList.addNote(new Note(tokens, lane, signatures.size()));
                                    	}
                                    	else{
                                    		System.out.println("Signature match!");
                                    		tempList.addNote(new Note(tokens, lane, sigcheck+1));
                                    	}
									}
									
								}
								prevChunkSecondHalf[lane] = getTotalAmp(Arrays.copyOfRange(chunk, (int)byteArraySize[lane]/2, (int)byteArraySize[lane]));
                                //if (prevChunkSecondHalf[lane] < 20000 && prevChunkSecondHalf[lane] > 5000 && lane == 2)
                                //    System.out.println("pcsh at this point is: " + prevChunkSecondHalf[lane]);
                                /*if (prevChunkSecondHalf < 20000 && prevChunkSecondHalf > 1000 && lane == 2){
                                    for (int z = 0; z < 4; z++){
                                        //System.out.println("Chunk length: " + chunk.length);
                                        //System.out.println("Position in chunk: " + (((int)byteArraySize[lane]/2) + (z*(int)byteArraySize[lane]/8)));
                                        System.out.println(getTotalAmp(Arrays.copyOfRange(chunk, chunk.length/2 + z*chunk.length/8, chunk.length/2 + (z+1)*chunk.length/8)));
                                    }
                                }*/
						}
					}
                }
                noteLists.add(tempList);
                checkEOF = checkIfEnd(n, files.size());
            }
        System.out.println("noteLists count: " + noteLists.size());
        return noteLists;
    }
    	
	public int getTotalAmp(byte[] b){
		int total = 0;
		//System.out.println("Chunk length: " + b.length);
		for(int i = 0; i < b.length; i++){
			//if (i<100) System.out.print(b[i] + " ");
			total += Math.abs(b[i]);
		}
		
		return total;
	}
	
	public boolean checkIfEnd(int[] n, int filenum){
		boolean x = true;
		for(int i = 0; i < filenum; i++){
			if (n[i] == -1)
				x = x && true;
			else
				x = x && false;
		}
		
		return x;
	}
	
	public void addSignature(byte[] chunk){
		//byte[] newSig = new byte[chunk.length];
        //System.arraycopy(chunk, 0, newSig, 0, chunk.length);
		byte[] newSig = Arrays.copyOf(chunk, chunk.length);
        signatures.add(newSig);
	}
	
	public int compareSignatures(byte[] chunk){
		for(int i = 0; i < signatures.size(); i++){
			System.out.println("Matching note with signature " + i);
			if(matchLPC(chunk, i))
				return i;
		}
		return -1;
	}
	
	public boolean matchLPC(byte[] chunk, int i){
		int lag = 60;
		double[] chunkAC = new double[lag];
		double[] sigAC = new double[lag];
		double[] chunkLPC = new double[lag-1];
		double[] sigLPC = new double[lag-1];
		double[] chunkref = new double[lag-1];
		double[] sigref = new double[lag-1];
		
		LPC.autocorr(chunk, chunkAC, lag, chunk.length);
		LPC.autocorr(signatures.get(i), sigAC, lag, signatures.get(i).length);
		
		LPC.wld(chunkLPC, chunkAC, chunkref, lag-1);
		LPC.wld(sigLPC, sigAC, sigref, lag-1);
		
		double sum = 0;
		
		for (int j = 0; j < lag-1; j++){
			sum += Math.pow((chunkLPC[j] - sigLPC[j]), 2);
		}
		
		System.out.println("Sum of LPC between current note and signature " + i + " is: " + sum);
		
		return (sum < 0.01);
	}
	
	/*
	public int compareSignatures(byte[] chunk){
		for(int i = 0; i < signatures.size(); i++){
			System.out.println("Matching note with signature " + i);
			if(matchDTW(chunk, i))
				return i;
		}
		return -1;
	}
	
	public boolean matchDTW(byte[] chunk, int sigIndex){
		int[][] distMatrix = new int[chunk.length/10][signatures.get(sigIndex).length/10];
		
		for(int i = 0; i < distMatrix.length; i++){
			int totalC = 0;
			
			for(int l = 0; l < 10; l++){
				totalC += chunk[i*10 + l];
			}
			
			for(int j = 0; j < distMatrix[0].length; j++){
				int totalS = 0;
				
				for(int k = 0; k < 10; k++){
					totalS += (signatures.get(sigIndex))[j*10 + k];
				}
				
				distMatrix[i][j] = (int) (Math.pow((totalC - totalS), 2));
			}
		}
		
		int shortest = getMin(distMatrix, 0, 0, 0, 0, 0);
		
		System.out.println("Shortest path: " + shortest);
		if (shortest < 5000) return true;
		return false;
	}
	
	public int getMin(int[][] distMatrix, int up, int right, int upright, int x, int y){
		int nextup = -1, nextright = -1, nextupright = -1;
		
		if (y < (distMatrix.length-1))
			nextup = getMin(distMatrix, up + distMatrix[y+1][x] - distMatrix[y][x], right, upright, x, y+1);
		if (x < (distMatrix[0].length-1))
			nextright = getMin(distMatrix, up, right + distMatrix[y][x+1] - distMatrix[y][x], upright, x+1, y);
		if (y < (distMatrix.length-1) && x < (distMatrix[0].length-1))
			nextupright = getMin(distMatrix, up, right, upright + distMatrix[y+1][x+1] - distMatrix[y][x], x+1, y+1);
		
		
		if (nextup == -1) return Math.min(nextright, nextupright);
		if (nextright == -1) return Math.min(nextup, nextupright);
		if (nextupright == -1) return Math.min(nextup, nextright);
		if (x == distMatrix[0].length-1 && y == distMatrix.length-1) return Math.min(up, Math.min(nextup,  nextright));
		return Math.min(nextup, Math.min(nextright, nextupright));
	}
	*/
    
    public void closeFiles(){
        try{
        while(files.size() > 0)
			if (files.get(0) != null) files.remove(0).close();
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

}