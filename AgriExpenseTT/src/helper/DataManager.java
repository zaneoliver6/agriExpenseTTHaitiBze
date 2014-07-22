package helper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.example.agriexpensett.cycleendpoint.model.Cycle;
import com.example.agriexpensett.cycleuseendpoint.model.CycleUse;
import com.example.agriexpensett.rpurchaseendpoint.model.RPurchase;

import dataObjects.localCycle;
import dataObjects.localCycleUse;
import dataObjects.localResourcePurchase;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public class DataManager {
	SQLiteDatabase db;
	DbHelper dbh;
	Context context;
	TransactionLog tL;
	public DataManager(Context context){
		dbh= new DbHelper(context);
		db=dbh.getReadableDatabase();
		this.context=context;
		tL=new TransactionLog(dbh,db);
	}
	public DataManager(Context context,SQLiteDatabase db,DbHelper dbh){
		this.dbh= dbh;
		this.db=db;
		this.context=context;
		tL=new TransactionLog(dbh,db);
	}

	public void insertCycle(int cropId, String landType, double landQty,long time){
		//insert into database
		int id=DbQuery.insertCycle(db, dbh, cropId, landType, landQty,tL,time);
		//insert into transaction table
		DbQuery.insertRedoLog(db, dbh, DbHelper.TABLE_CROPCYLE, id, "ins");
		//try insert into cloud
		CloudInterface c= new CloudInterface(context,db,dbh);// new CloudInterface(context);
		c.insertCycleC();
		//update database last updated time
	}
	public void insertPurchase( int resourceId, String quantifier, double qty,String type,double cost){
		//insert into database
		int id=DbQuery.insertResourceExp(db, dbh, type, resourceId, quantifier, qty, cost, tL);
		//insert into redo log table
		DbQuery.insertRedoLog(db, dbh, DbHelper.TABLE_RESOURCE_PURCHASES, id, "ins");
		//try to insert into cloud
		CloudInterface c= new CloudInterface(context,db,dbh);//new CloudInterface(context);
		c.insertPurchase();
	}
	
	//pass in the cycleId
	public void deleteCycle( int id){
		//delete from database
		DbQuery.deleteRecord(db, dbh, DbHelper.TABLE_CROPCYLE,id);
		//insert into transaction table
		DbQuery.insertRedoLog(db, dbh, DbHelper.TABLE_CROPCYLE, id,"del");
		//try delete from cloud
		CloudInterface c= new CloudInterface(context,db,dbh);//new CloudInterface(context);
		c.deleteCycle();
			//if successful
				//delete from transaction table
				//and update cloud's last updated time
		//update database last updated time
	}
	
	//------------------------------------------READY TO USE (FROM FRONT)
	public void deleteCycleUse(localCycleUse l){
		//update the Purchase that was used (local) add back the amount that was used
		//update cloud, record it in the redo Log purchase Id and the table
		//update the Cycle's total spent (local) subtract the usage cost from the cycle's total spent
		//update cloud, record the update in the redo log
		//finally delete cycleUse (locally)
		//delete cycleUse in cloud, by recording the delete in the redo log
		
		//PURCHASE
		//updating local Purchase
		RPurchase p=DbQuery.getARPurchase(db, dbh, l.getPurchaseId());
		ContentValues cv=new ContentValues();
		cv.put(DbHelper.RESOURCE_PURCHASE_REMAINING, (l.getAmount()+p.getQtyRemaining()) );
		db.update(DbHelper.TABLE_RESOURCE_PURCHASES, cv, DbHelper.RESOURCE_PURCHASE_ID+"="+l.getPurchaseId(), null);
		//record transaction in log
		tL.insertTransLog(DbHelper.TABLE_RESOURCE_PURCHASES, l.getPurchaseId(), TransactionLog.TL_DEL);
		//redo log (cloud)
		DbQuery.insertRedoLog(db, dbh, DbHelper.TABLE_RESOURCE_PURCHASES, l.getPurchaseId(),TransactionLog.TL_UPDATE);
		
		//CYCLE
		//updating local Cycle
		Cycle c=DbQuery.getCycle(db, dbh, l.getCycleid());
		cv=new ContentValues();
		cv.put(DbHelper.CROPCYCLE_TOTALSPENT, (c.getTotalSpent()-l.getUseCost()) );
		db.update(DbHelper.TABLE_CROPCYLE, cv, DbHelper.CROPCYCLE_ID+"="+l.getCycleid(), null);
		//record transaction in log
		tL.insertTransLog(DbHelper.TABLE_CROPCYLE, l.getCycleid(), TransactionLog.TL_UPDATE);
		//redo log (cloud)
		DbQuery.insertRedoLog(db, dbh, DbHelper.TABLE_CROPCYLE, l.getCycleid(), TransactionLog.TL_UPDATE);
		
		//CYCLEUSE
		//Delete CycleUse
		db.delete(DbHelper.TABLE_CYCLE_RESOURCES, DbHelper.CYCLE_RESOURCE_ID+"="+l.getId(), null);
		tL.insertTransLog(DbHelper.TABLE_CYCLE_RESOURCES, l.getId(), TransactionLog.TL_DEL);
		//redo log (cloud)
		DbQuery.insertRedoLog(db, dbh, DbHelper.TABLE_CYCLE_RESOURCES, l.getId(), TransactionLog.TL_DEL);
	}
	
	//--------------------------------------READY TO USE (FROM FRONT)
	public void deletePurchase(RPurchase p){
		//get all cycleUse with the purchase's id
		//delete each one using the deleteCycleUse to remove the cost added to each cycle
		//delete the purchase (locally)
		//put the delete in the redo log
		
		//getting all the cycleUse
		ArrayList<localCycleUse> list=new ArrayList<localCycleUse>();
		DbQuery.getCycleUseP(db, dbh, p.getPId(), list, null);
		Iterator<localCycleUse> itr=list.iterator();
		while(itr.hasNext()){
			localCycleUse l=itr.next();
			this.deleteCycleUse(l);//already does the recording into the redo log(cloud) and transaction log
		}
		//delete purchase 
		db.delete(DbHelper.TABLE_RESOURCE_PURCHASES, DbHelper.RESOURCE_PURCHASE_ID+"="+p.getPId(), null);
		tL.insertTransLog(DbHelper.TABLE_RESOURCE_PURCHASES, p.getPId(), TransactionLog.TL_DEL);
		//redo log (cloud)
		DbQuery.insertRedoLog(db, dbh, DbHelper.TABLE_RESOURCE_PURCHASES, p.getPId(), TransactionLog.TL_DEL);
	}
	
	//-----------------------------------READY TO USE (FROM FRONT)
	public void deleteCycle(localCycle c){
		//get all cycleUse wih cid
		//delete each one using delete cycleUse as to restore to purchase the amounts used by the cycleUse
		//delete the cycle Locally
		//insert into redo log (cloud)

		ArrayList<localCycleUse> list=new ArrayList<localCycleUse>();
		DbQuery.getCycleUse(db, dbh, c.getId(), list, null);
		Iterator<localCycleUse> itr=list.iterator();
		while(itr.hasNext()){
			localCycleUse l=itr.next();
			this.deleteCycleUse(l);//already does the recording into the redo log(cloud) and transaction log
		}
		//delete cycle
		db.delete(DbHelper.TABLE_CROPCYLE, DbHelper.CROPCYCLE_ID+"="+c.getId(), null);
		tL.insertTransLog(DbHelper.TABLE_CROPCYLE, c.getId(), TransactionLog.TL_DEL);
		//insert into redo log (cloud)
		DbQuery.insertRedoLog(db, dbh, DbHelper.TABLE_CROPCYLE, c.getId(), TransactionLog.TL_DEL);
	}
	//---------------------- READY TO USE [WITHOUT INCLUSION OF RESOURCES TABLE]
	public void deleteResource(int rId){
		//get all purchases of rId
		//delete them all using the deletePurchase above (ready to use version)
		//get all cycles who's cycleId is rId
		//delete them all using the deleteCycle above (ready to use version)
		//delete resource and record in transaction log
		//-----Not Sure If we're having resources in the cloud

		ArrayList<localResourcePurchase> pList=new ArrayList<localResourcePurchase>();
		DbQuery.getResourcePurchases(db, dbh, pList, rId);
		Iterator<localResourcePurchase>pI=pList.iterator();
		while(pI.hasNext()){
			this.deletePurchase(pI.next().toRPurchase());
		}
		
		ArrayList<localCycle> cList=new ArrayList<localCycle>();
		DbQuery.getCycles(db, dbh, cList);
		Iterator<localCycle> cI=cList.iterator();
		while(cI.hasNext()){
			localCycle c=cI.next();
			if(c.getCropId()==rId)
				this.deleteCycle(c);
		}
		//delete resource
		db.delete(DbHelper.TABLE_RESOURCES, DbHelper.RESOURCES_ID+"="+rId, null);
		//not bothering to record in transaction log if not storing resources in clouf
		//not bothering to store in redo log if res not going to cloud
	}
	
	public void insertCycleUse(int cycleId, int resPurchaseId, double qty,String type,String quantifier,double useCost){
		
		//insert into database
		int id=DbQuery.insertResourceUse(db, dbh, cycleId, type, resPurchaseId, qty,quantifier,useCost, tL);
		//insert into redo log table
		DbQuery.insertRedoLog(db, dbh, DbHelper.TABLE_CYCLE_RESOURCES, id, "ins");
		//try to insert into cloud
		CloudInterface c= new CloudInterface(context,db,dbh);//new CloudInterface(context);
		c.insertCycleUseC();
	}
	
	
	public void update(){
		//check time-updated in cloud
		long locaupdated;
		//check time-updated in local
		long cloudupdated;
		//if both are the same 
			//then do nothing
		
		//if cloud is more recent
			updateLocal();
		
		//if local is more recent
			
	}
	private void updateLocal(){
		//since data is being pulled from a network we are using an async task
		//since the async task is being run on a diff thread asynchronously
			//we must do the work there because we wont know when it returns
		CloudInterface c= new CloudInterface(context);
		//get all recrods from cloud (FIRST)
			//-- get lists of all object kinds in database
			//c.updateLocal();
		tL.updateLocal(0);
		
		System.out.println("meh");
		//delete all records from local db   
			//-- drop tables then recreate
		//write lists to local database
			//-- iterator and insert
		//set local db last updated to cloud's last updated
	}
	private void updateCloud(){ 
			//-- put all inserts in the redo log
			//-- call the insert method for each kind
		//set cloud last updated to local db's last updated
	}
	public void updatePurchase(RPurchase p,ContentValues cv){
		db.update(DbHelper.TABLE_RESOURCE_PURCHASES, cv, DbHelper.RESOURCE_PURCHASE_ID+"="+p.getPId(),null);
		//update the cloud
		DbQuery.insertRedoLog(db, dbh, DbHelper.TABLE_RESOURCE_PURCHASES,p.getPId(), TransactionLog.TL_UPDATE);
		//record in transaction log
		TransactionLog tl=new TransactionLog(dbh, db);
		tl.insertTransLog(DbHelper.TABLE_RESOURCE_PURCHASES, p.getPId(), TransactionLog.TL_UPDATE);
		
		CloudInterface cloud= new CloudInterface(context,db,dbh);// new CloudInterface(context);
		cloud.updatePurchaseC();
	}
	public void updateCycle(localCycle c,ContentValues cv){
		db.update(DbHelper.TABLE_CROPCYLE, cv, DbHelper.CROPCYCLE_ID+"="+c.getId(), null);
		//update the cloud
		DbQuery.insertRedoLog(db, dbh, DbHelper.TABLE_CROPCYLE, c.getId(), TransactionLog.TL_UPDATE);
		//record in transaction log
		TransactionLog tl=new TransactionLog(dbh, db);
		tl.insertTransLog(DbHelper.TABLE_CROPCYLE, c.getId(),TransactionLog.TL_UPDATE);
		
		CloudInterface cloud= new CloudInterface(context,db,dbh);// new CloudInterface(context);
		cloud.updateCycleC();
	}
	
	//------------------------------------------------------------------fixed deletes
	public void delPurchase(int pId){
		String code="select * from "+DbHelper.TABLE_CYCLE_RESOURCES+" where "
				+DbHelper.CYCLE_RESOURCE_PURCHASE_ID+"="+pId;
		Cursor cursor=db.rawQuery(code, null);
		if(cursor.getCount()<1)
			return;
		
		while(cursor.moveToNext()){
			int cId=cursor.getInt(cursor.getColumnIndex(DbHelper.CYCLE_RESOURCE_CYCLEID));
			
			try{
				db.delete(DbHelper.TABLE_CYCLE_RESOURCES, DbHelper.CYCLE_RESOURCE_CYCLEID+"="+cId, null);
				db.delete(DbHelper.TABLE_CROPCYLE, DbHelper.CROPCYCLE_ID+"="+cId, null);
			}catch(Exception e){
				e.printStackTrace();
			}
			System.out.println("del");
		}
		db.delete(DbHelper.TABLE_RESOURCE_PURCHASES, DbHelper.RESOURCE_PURCHASE_ID+"="+pId, null);
	}
	
	public void delResource(int resId){
		String code="select * from "+DbHelper.TABLE_RESOURCE_PURCHASES+" where "
				+DbHelper.RESOURCE_PURCHASE_RESID+"="+resId;
		db.delete(DbHelper.TABLE_RESOURCES, DbHelper.RESOURCES_ID+"="+resId, null);
		db.delete(DbHelper.TABLE_CROPCYLE, DbHelper.CROPCYCLE_CROPID+"="+resId, null);
		Cursor cursor=db.rawQuery(code, null);
		if(cursor.getCount()<1)
			return;
		while(cursor.moveToNext()){
			int pId=cursor.getInt(cursor.getColumnIndex(DbHelper.RESOURCE_PURCHASE_ID));
			delPurchase(pId);
		}
	}
	public void insertResource(String name,String type) {
		ContentValues cv=new ContentValues();
		cv.put(DbHelper.RESOURCES_NAME, name);
		cv.put(DbHelper.RESOURCES_TYPE, type);
		db.insert(DbHelper.TABLE_RESOURCES, null, cv);
	}
}
