import React from 'react';
import { CardResult, SearchView} from 'grove-core-react-components';

const CustomSearchResultContent = props => {
{
		console.log("Content" + props.result.extracted.content );
        return props.result.extracted.content.map( match => {
          return(
            <div className="ml-search-result-matches" >
               <div className="ml-search-result-matches">
                    <div className="ml-search-result-matches">
                        Job ID : {match.batchJobId && match.batchJobId}
                     </div>
					 <div className="ml-search-result-matches">
                        Job Name : {match.batchJobName && match.batchJobName}
                     </div>
					 <div className="ml-search-result-matches">
                        Batch Start : {match.batchJobStartTs && match.batchJobStartTs}
                     </div>
					 <div className="ml-search-result-matches">
                        Batch End : {match.batchJobEndTs && match.batchJobEndTs}
                     </div>					 
               </div>
            </div>
            );
        })
  }
};

 const myCustomHeader = props => {
   let myHdrL1;
   let myJobName;
   let myContent;
   let runDate;
   let runTs;
   if (props.result.extracted) {
   myContent = props.result.extracted.content[0];
   runDate = myContent.runTs.split(" ")[0];
   runTs = myContent.runTs.split(" ")[1].split(".")[0];
   myJobName = myContent.batchJobName;
   if (myJobName) { myHdrL1 = "" + myJobName; }
   }
   return (
     <h4><b>{myHdrL1}</b><p>{runDate} {runTs}</p></h4>
   )
 }

const CustomSearchResult = props => {
  return (
    <CardResult
      result={props.result}
      detailPath={props.detailPath}
      header={myCustomHeader}
      content={CustomSearchResultContent}
    />
  );
};

export default CustomSearchResult;
