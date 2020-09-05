import React, { Component } from 'react';
import { Row, Col, Tab, Tabs } from 'react-bootstrap';

 const MyDetailTemplate = (props) => {
	 
  const obj = props.detail
   return (
    <div>
      <h1>Batch Job Result Details</h1>
	  <Row>
	    <Col md={1}><b>RunDate</b></Col>
        <Col md={11}>{obj.runTs.split(" ")[0]}</Col>
	  </Row>
	  <Row>
	    <Col md={1}><b>Result</b></Col>
        <Col md={11}>{obj.result}</Col>
	  </Row>
	  <Row>
	    <Col md={1}><b>Message</b></Col>
        <Col md={11}>{obj.message}</Col>
	  </Row>	  
	  <Row>
	    <Col md={1}><b>Description</b></Col>
        <Col md={11}>{obj.batchJobDescription}</Col>
	  </Row>
	  <Row>
	    <Col md={1}><b>URI</b></Col>
        <Col md={11}>{obj.batchResultUri}</Col>
	  </Row>
	  <Row>
	    <Col md={1}><b>Job Id</b></Col>
        <Col md={11}>{obj.batchJobId}</Col>
	  </Row>
	  <Row>
	    <Col md={1}><b>Records Processed</b></Col>
        <Col md={11}>{obj.batchResultCount}</Col>
	  </Row>
	  <Row>
	    <Col md={1}><b>Process Time</b></Col>
        <Col md={11}><i>From:</i> {obj.batchJobStartTs} <i>To:</i> {obj.batchJobEndTs} </Col>
	  </Row>
	  <Row>
	    <Col md={1}><b>Process Duration</b></Col>
        <Col md={11}>{obj.batchDuration}</Col>
	  </Row>
	  <Row>
	    <Col md={1}><b>Query Run</b></Col>
        <Col md={11}>{obj.query}</Col>
	  </Row>
	  <Row>
	    <Col md={1}><b>Run By</b></Col>
        <Col md={11}>{obj.runBy}</Col>
	  </Row>	  
    </div>
   );
 };

 export default MyDetailTemplate;