/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  The ASF licenses this file to You
 * under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.  For additional information regarding
 * copyright in this work, please see the NOTICE file in the top level
 * directory of this distribution.
 */

/**
 * A utility class that search simple db for the node type provided and returns a list of hostnames as a string array
 */
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.ec2.model.*

class NodeRegistry {


    public static final String TAG_PREFIX = "tag:"
    //taken from aws
    public static final String STACK_NAME = "usergrid:stack-name";
    public static final String NODE_TYPE = "usergrid:node_type";

    private String accessKey = (String) System.getenv().get("AWS_ACCESS_KEY")
    private String secretKey = (String) System.getenv().get("AWS_SECRET_KEY")
    private String stackName = (String) System.getenv().get("STACK_NAME")
    private String instanceId = (String) System.getenv().get("EC2_INSTANCE_ID");
    private String region = (String) System.getenv().get("EC2_REGION");
    private String domain = stackName

    private BasicAWSCredentials creds;
    private AmazonEC2Client ec2Client;


    NodeRegistry() {

        if (region == null) {
            throw new IllegalArgumentException("EC2_REGION must be defined")
        }

        if (instanceId == null) {
            throw new IllegalArgumentException("EC2_INSTANCE_ID must be defined")
        }

        if (stackName == null) {
            throw new IllegalArgumentException("STACK_NAME must be defined")
        }

        if (accessKey == null) {
            throw new IllegalArgumentException("AWS_ACCESS_KEY must be defined")
        }

        if (secretKey == null) {
            throw new IllegalArgumentException("AWS_SECRET_KEY must be defined")
        }

        creds = new BasicAWSCredentials(accessKey, secretKey)
        ec2Client = new AmazonEC2Client(creds)
        def regionEnum = Regions.fromName(region);
        ec2Client.setRegion(Region.getRegion(regionEnum))


    }

    /**
     * Search for the node type, return a string array of hostnames that match it within the running domain
     * @param defNodeType
     */
    def searchNode(def nodeType) {

        def stackNameFilter = new Filter(TAG_PREFIX+STACK_NAME).withValues(stackName)
        def nodeTypeFilter = new Filter(TAG_PREFIX+NODE_TYPE).withValues(nodeType)



         //sort by created date
        def servers = new TreeSet<ServerEntry>();


        def token = null



        while(true){

            def describeRequest = new DescribeInstancesRequest().withFilters(stackNameFilter, nodeTypeFilter)

            if(token != null){
                describeRequest.withNextToken(token);
            }


            def nodes = ec2Client.describeInstances(describeRequest)

            for (reservation in nodes.getReservations()) {

                //TODO, add these to a list then sort them by date, then name
                for (instance in reservation.getInstances()) {

                    //ignore instances that aren't running
                    if (instance.state.getName() == InstanceStateName.Running.toString()) {
                        servers.add(new ServerEntry(instance.launchTime, instance.publicDnsName));
                    }


                }

            }

            //nothing to do, exit the loop
            if(nodes.nextToken != null){
                token = nodes.nextToken;
            }
            else{
                break;
            }




        }




        return createResults(servers);
    }

    def createResults(def servers){

        def results = [];

        for(server in servers){
            results.add(server.publicIp)
        }

        return results;
    }



    /**
     * Add the node to the database if it doesn't exist
     */
    def addNode(def nodeType) {

        //add the node type
        def tagRequest = new CreateTagsRequest().withTags(new Tag(NODE_TYPE, nodeType), new Tag(STACK_NAME, stackName)).withResources(instanceId)



        ec2Client.createTags(tagRequest)


    }


    class ServerEntry implements Comparable<ServerEntry>{
        private final Date launchDate;
        private final String publicIp;

        ServerEntry(final Date launchDate, final String publicIp) {
            this.launchDate = launchDate
            this.publicIp = publicIp
        }

        @Override
        int compareTo(final ServerEntry o) {
            if(launchDate.before(o.launchDate)){
                -1;
            }else if (launchDate.after(o.launchDate)){
                return 1;
            }

            return publicIp.compareTo(o.publicIp);


        }

        boolean equals(final o) {
            if (this.is(o)) return true
            if (getClass() != o.class) return false

            final ServerEntry that = (ServerEntry) o

            if (launchDate != that.launchDate) return false
            if (publicIp != that.publicIp) return false

            return true
        }

        int hashCode() {
            int result
            result = launchDate.hashCode()
            result = 31 * result + publicIp.hashCode()
            return result
        }
    }

}
